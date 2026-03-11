package tech.torlando.rns.data

data class DiscoveredInterface(
    val name: String,
    val type: String,
    val status: String,
    val transport: Boolean,
    val transportId: String,
    val hops: Int,
    val stampValue: Int,
    val reachableOn: String?,
    val port: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val height: Double?,
    val ifacNetname: String?,
    val ifacNetkey: String?,
    val lastHeard: Double,
    val discovered: Double,
    val heardCount: Int,
    val configEntry: String?,
)
