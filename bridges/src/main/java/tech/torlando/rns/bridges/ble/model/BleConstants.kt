package tech.torlando.rns.bridges.ble.model

import java.util.UUID

object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("37145b00-442d-4a94-917f-8f42c5da28e3")
    val CHARACTERISTIC_RX_UUID: UUID = UUID.fromString("37145b00-442d-4a94-917f-8f42c5da28e5")
    val CHARACTERISTIC_TX_UUID: UUID = UUID.fromString("37145b00-442d-4a94-917f-8f42c5da28e4")
    val CHARACTERISTIC_IDENTITY_UUID: UUID = UUID.fromString("37145b00-442d-4a94-917f-8f42c5da28e6")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val MAX_CONNECTIONS = 7
    const val MIN_RSSI_DBM = -85
    const val MAX_MTU = 517
    const val MIN_MTU = 23
    const val DEFAULT_MTU = 185

    const val DISCOVERY_INTERVAL_MS = 5000L
    const val DISCOVERY_INTERVAL_IDLE_MS = 30000L
    const val SCAN_DURATION_MS = 10000L

    const val CONNECTION_TIMEOUT_MS = 30000L
    const val OPERATION_TIMEOUT_MS = 5000L
}
