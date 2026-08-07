package vpn.sdk

/** A plain (host, port) pair - [VpnSDK.extractHostPort]'s return type, kept outside `VpnSDK`
 *  itself so Java callers get an ordinary two-field class rather than a Kotlin `Pair<String,
 *  Int>`, whose generic boxing is awkward to read from Java. */
data class HostPort(
    @JvmField val host: String,
    @JvmField val port: Int,
)
