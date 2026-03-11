package tech.torlando.rns.rnode.data

/**
 * Output of the RNode wizard — a flat, module-independent representation
 * of a configured RNode interface. The host app maps this to its own
 * persistence model (e.g. InterfaceConfig.RNodeInterface).
 */
data class RNodeWizardResult(
    val name: String,
    /** "classic", "ble", "tcp", or "usb" */
    val connectionMode: String,
    /** BT device name, host:port, or empty for USB */
    val targetDevice: String,
    val frequency: Long,
    val bandwidth: Int,
    val spreadingFactor: Int,
    val codingRate: Int,
    val txPower: Int,
    /** "full", "gateway", "boundary", etc. */
    val interfaceMode: String,
    val stAlock: String,
    val ltAlock: String,
)
