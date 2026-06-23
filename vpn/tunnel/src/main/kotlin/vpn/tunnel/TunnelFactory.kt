package vpn.tunnel

import android.content.Context
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.NoopTunnelActionHandler

object TunnelFactory {

    @Volatile
    private var tunnelManager: TunnelManager? = null

    @Volatile
    var lastClientIP: String? = null
        private set

    @Volatile
    var lastServerIP: String? = null
        private set

    @Synchronized
    fun setup(context: Context) {
        val handler = NoopTunnelActionHandler()
        val backend = GoBackend(context.applicationContext, handler)

        val vpnTunnel = VpnTunnel()
        val adapter = AwgBackendAdapter(backend, vpnTunnel)
        tunnelManager = TunnelManager(adapter)
    }

    fun getTunnelManager(): VpnTunnelManager<VpnConfig.Awg> {
        return tunnelManager
            ?: throw IllegalStateException("TunnelFactory is not initialized. Call setup() first.")
    }

    private fun parseEndpointHost(rawConfig: String): String? {
        val match = Regex("""(?m)^\s*Endpoint\s*=\s*(.+)\s*$""").find(rawConfig) ?: return null
        val value = match.groupValues[1].trim()
        return if (value.startsWith("[")) {
            value.substringAfter("[").substringBefore("]")
        } else {
            value.substringBeforeLast(":")
        }
    }

    @Synchronized
    fun connectWithRawConfig(rawConfig: String) {
        val parsed = ConfigMapper.parseConfig(rawConfig)
        lastClientIP = parsed.getInterface().addresses.firstOrNull()?.address?.hostAddress
        lastServerIP = parseEndpointHost(rawConfig)
        val config = VpnConfig.Awg(parsed)
        getTunnelManager().setState(VpnTunnelState.UP, config)
    }

    @Synchronized
    fun disconnect() {
        getTunnelManager().setState(VpnTunnelState.DOWN, null)
    }
}
