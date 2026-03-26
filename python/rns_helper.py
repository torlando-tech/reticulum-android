"""
Helper for managing RNS lifecycle on Android.

Handles two issues that prevent clean restart:
1. signal.signal() fails from non-main threads (Android calls from coroutines)
2. RNS uses class-level singletons/state that aren't cleared by exit_handler()
"""

import os
import signal
# Patch signal before RNS imports it
signal.signal = lambda *a, **kw: None

# Patch os._exit to raise SystemExit instead of hard-killing the process.
# RNS calls os._exit() on unhandled exceptions, which bypasses all try/except
# and kills the entire Android app. Converting to SystemExit lets us catch it.
_real_os_exit = os._exit
def _safe_exit(code=0):
    raise SystemExit(code)
os._exit = _safe_exit

import RNS
import sys
import socket
import time

# Disable epoll backend for RNS interfaces. The BackboneInterface epoll job
# thread runs `while True` as a daemon and cannot be cleanly stopped for
# in-process restart — its sockets survive shutdown and block port rebinding.
# The ThreadingTCPServer/threading fallback supports deterministic shutdown.
import RNS.vendor.platformutils as _pu
_pu.use_epoll = lambda: False

# Active RNode interfaces created via create_rnode_interface()
_rnode_interfaces = []


def _get_classes():
    """Get the actual Reticulum and Transport classes via every known path."""
    ret_classes = set()
    trn_classes = set()

    # Via sys.modules
    ret_mod = sys.modules.get("RNS.Reticulum")
    trn_mod = sys.modules.get("RNS.Transport")
    if ret_mod:
        cls = getattr(ret_mod, "Reticulum", None)
        if cls: ret_classes.add(cls)
    if trn_mod:
        cls = getattr(trn_mod, "Transport", None)
        if cls: trn_classes.add(cls)

    # Via RNS package attributes
    obj = getattr(RNS, "Reticulum", None)
    if obj is not None:
        ret_classes.add(obj)
    obj = getattr(RNS, "Transport", None)
    if obj is not None:
        if hasattr(obj, "Transport"):
            trn_classes.add(obj.Transport)
        else:
            trn_classes.add(obj)

    return ret_classes, trn_classes


def _reset_all():
    """Reset all RNS singleton state."""
    ret_classes, trn_classes = _get_classes()

    # Reset Reticulum singleton
    for cls in ret_classes:
        try: cls._Reticulum__instance = None
        except Exception: pass
        try: cls._Reticulum__exit_handler_ran = False
        except Exception: pass
        try: cls._Reticulum__interface_detach_ran = False
        except Exception: pass

    # Reset Transport state
    transport_lists = [
        "interfaces", "destinations", "pending_links", "active_links",
        "receipts", "announce_handlers", "discovery_pr_tags",
        "control_destinations", "control_hashes", "mgmt_destinations",
        "mgmt_hashes", "remote_management_allowed", "local_client_interfaces",
        "local_client_rssi_cache", "local_client_snr_cache",
        "local_client_q_cache",
    ]
    transport_dicts = [
        "announce_table", "path_table", "reverse_table", "link_table",
        "held_announces", "tunnels", "announce_rate_table", "path_requests",
        "path_states", "discovery_path_requests", "pending_local_path_requests",
        "blackholed_identities",
    ]
    transport_sets = ["packet_hashlist", "packet_hashlist_prev"]

    for T in trn_classes:
        for attr in transport_lists:
            try: setattr(T, attr, [])
            except Exception: pass
        for attr in transport_dicts:
            try: setattr(T, attr, {})
            except Exception: pass
        for attr in transport_sets:
            try: setattr(T, attr, set())
            except Exception: pass
        try: T.identity = None
        except Exception: pass
        try: T.owner = None
        except Exception: pass
        try: T.start_time = None
        except Exception: pass
        try: T.jobs_locked = False
        except Exception: pass
        try: T.jobs_running = False
        except Exception: pass
        try: T.links_last_checked = 0.0
        except Exception: pass
        try: T.receipts_last_checked = 0.0
        except Exception: pass
        try: T.announces_last_checked = 0.0
        except Exception: pass
        try: T.pending_prs_last_checked = 0.0
        except Exception: pass


def set_rnode_bridge(bridge):
    """Set the KotlinRNodeBridge instance for Python RNode interfaces."""
    import rnode_interface
    rnode_interface.set_rnode_bridge(bridge)


def set_usb_bridge(bridge):
    """Set the KotlinUSBBridge instance for Python USB interfaces."""
    import usb_bridge
    usb_bridge.set_usb_bridge(bridge)


def create_rnode_interface(name, connection_mode, target_device, frequency, bandwidth, spreading_factor, coding_rate, tx_power, enable_framebuffer=True):
    """Create an RNode interface and register it with RNS Transport."""
    import rnode_interface as rni

    # Get the Reticulum instance as the owner
    ret_classes, _ = _get_classes()
    owner = None
    for cls in ret_classes:
        owner = getattr(cls, "_Reticulum__instance", None)
        if owner is not None:
            break

    config = {
        "connection_mode": connection_mode,
        "target_device_name": target_device,
        "frequency": int(frequency),
        "bandwidth": int(bandwidth),
        "spreading_factor": int(spreading_factor),
        "coding_rate": int(coding_rate),
        "tx_power": int(tx_power),
        "enable_framebuffer": bool(enable_framebuffer),
    }

    iface = rni.RNodeInterface(owner, name, config)
    _rnode_interfaces.append(iface)
    RNS.log(f"Created RNode interface: {name}", RNS.LOG_INFO)

    # Start the interface (connect to device and configure radio)
    if iface.start():
        # Register with RNS Transport so it can route packets
        RNS.Transport.interfaces.append(iface)
        RNS.log(f"RNode interface '{name}' started and registered with Transport", RNS.LOG_INFO)
    else:
        RNS.log(f"RNode interface '{name}' failed to start", RNS.LOG_ERROR)

    return iface


def destroy_rnode_interfaces():
    """Tear down all RNode interfaces."""
    global _rnode_interfaces
    for iface in _rnode_interfaces:
        try:
            iface.stop()
        except Exception:
            pass
    _rnode_interfaces = []


def is_connected_to_shared_instance():
    """Check if this instance connected to an existing shared instance."""
    ret_classes, _ = _get_classes()
    for cls in ret_classes:
        instance = getattr(cls, "_Reticulum__instance", None)
        if instance is not None:
            return getattr(instance, "is_connected_to_shared_instance", False)
    return False


def enable_discovery():
    """Enable interface discovery listener."""
    try:
        RNS.Transport.discover_interfaces()
        return True
    except Exception as e:
        return False


def list_discovered():
    """List discovered interfaces. Returns list of dicts."""
    try:
        handler = RNS.Transport.discovery_handler
        if handler is None:
            return []
        return handler.list_discovered_interfaces()
    except Exception:
        return []


def _shutdown_instance(instance):
    """Full teardown of an RNS instance."""
    # 1. Run exit_handler first — detaches all interfaces (with thread joins),
    #    saves state. This also detaches the LocalServerInterface (no-op) and
    #    LocalClientInterface (closes client sockets).
    try:
        type(instance).exit_handler()
    except Exception:
        pass

    # 2. Now shut down the ThreadingTCPServer. We do this AFTER exit_handler
    #    so all client connections are detached first — otherwise detach threads
    #    might reconnect to the server during shutdown.
    si = getattr(instance, 'shared_instance_interface', None)
    if si is not None:
        server = getattr(si, 'server', None)
        if server is not None:
            try:
                server.shutdown()
                server.server_close()
            except Exception:
                pass

    # 3. Wait for the port to be actually free.
    port = getattr(instance, 'local_interface_port', 37428)
    _wait_for_port_free("127.0.0.1", port)

    _reset_all()


def _wait_for_port_free(host, port, timeout=5.0):
    """Block until nothing is listening on host:port."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(0.5)
            s.connect((host, port))
            s.close()
            # Connection succeeded — something is still listening
            time.sleep(0.1)
        except (ConnectionRefusedError, OSError):
            # Connection refused — port is free
            return
    # Timed out, proceed anyway


def start(config_path):
    """Initialize RNS."""
    _reset_all()
    return RNS.Reticulum(config_path)


def stop():
    """Shut down RNS and reset singleton state for restart."""
    # 1. Destroy RNode interfaces (managed outside RNS Transport)
    destroy_rnode_interfaces()

    # 2. Full teardown: exit_handler + TCPServer shutdown + port wait + reset
    ret_classes, _ = _get_classes()
    for cls in ret_classes:
        instance = getattr(cls, "_Reticulum__instance", None)
        if instance is not None:
            _shutdown_instance(instance)
            return
    # No instance found, just reset state
    _reset_all()
