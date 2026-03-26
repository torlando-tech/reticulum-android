"""
Helper for managing RNS lifecycle on Android.

Runs in a separate process (:rns). Process death is the cleanup
mechanism — no need for complex port-waiting or singleton reset.
"""

import os
import signal
# Patch signal before RNS imports it (signal.signal() fails from non-main threads)
signal.signal = lambda *a, **kw: None

# Patch os._exit to raise SystemExit instead of hard-killing the process.
# RNS calls os._exit() on unhandled exceptions, which bypasses all try/except
# and kills the entire Android app. Converting to SystemExit lets us catch it.
_real_os_exit = os._exit
def _safe_exit(code=0):
    raise SystemExit(code)
os._exit = _safe_exit

import RNS

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
    import sys
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


def start(config_path):
    """Initialize RNS."""
    return RNS.Reticulum(config_path)


def stop():
    """Shut down RNS. Process exit handles final cleanup."""
    # Destroy RNode interfaces (managed outside RNS Transport)
    destroy_rnode_interfaces()

    # Run exit_handler to detach interfaces and save state
    ret_classes, _ = _get_classes()
    for cls in ret_classes:
        instance = getattr(cls, "_Reticulum__instance", None)
        if instance is not None:
            try:
                type(instance).exit_handler()
            except Exception:
                pass
            break
    # Process death (System.exit) handles the rest — no port waiting or reset needed
