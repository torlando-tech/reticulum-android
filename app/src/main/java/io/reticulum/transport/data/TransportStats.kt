package io.reticulum.transport.data

data class InterfaceStats(
    val name: String,
    val displayName: String = name,
    val rxb: Long = 0,
    val txb: Long = 0,
    val online: Boolean = false,
    val clients: Int = 0,
    val type: String = "",
    val parentInterfaceName: String? = null,
)

data class PathEntry(
    val hash: String,
    val via: String,
    val hops: Int,
    val expires: Long,
    val interfaceName: String,
)

data class TransportStats(
    val interfaces: List<InterfaceStats> = emptyList(),
    val pathTable: List<PathEntry> = emptyList(),
    val announceTable: List<AnnounceEntry> = emptyList(),
)

data class AnnounceEntry(
    val hash: String,
    val hops: Int,
    val timestamp: Double,
    val interfaceName: String,
)
