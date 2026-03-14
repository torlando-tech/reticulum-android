"""
USB Bridge for Reticulum Android RNode support.

Provides the Python-side reference to KotlinUSBBridge for USB serial
communication with RNode devices. The bridge instance is set from Kotlin
during initialization and retrieved by rnode_interface.py when operating
in USB mode.
"""

import RNS

# Global USB bridge instance (set from Kotlin during initialization)
_usb_bridge_instance = None


def set_usb_bridge(bridge):
    """Set the KotlinUSBBridge instance from Kotlin."""
    global _usb_bridge_instance
    _usb_bridge_instance = bridge
    RNS.log("USB bridge set", RNS.LOG_DEBUG)


def get_usb_bridge():
    """Get the KotlinUSBBridge instance, or None if not initialized."""
    return _usb_bridge_instance
