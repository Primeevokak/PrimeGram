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

    @Volatile
    private var appContext: Context? = null

    /**
     * Records the context but builds nothing.
     *
     * Constructing [GoBackend] loads the AmneziaWG gomobile library and boots a Go runtime.
     * Doing that from Application.onCreate cost every cold start that time — including for
     * the great majority of launches, which never bring up a tunnel at all. The backend is
     * now created on first real use, off the startup path.
     */
    @Synchronized
    fun setup(context: Context) {
        appContext = context.applicationContext
    }

    /** Builds the backend on first use. Do not call from the main thread. */
    @Synchronized
    fun getTunnelManager(): VpnTunnelManager<VpnConfig.Awg> {
        tunnelManager?.let { return it }
        val context = appContext
            ?: throw IllegalStateException("TunnelFactory is not initialized. Call setup() first.")
        val handler = NoopTunnelActionHandler()
        val backend = GoBackend(context, handler)
        val adapter = AwgBackendAdapter(backend, VpnTunnel())
        val manager = TunnelManager(adapter)
        tunnelManager = manager
        return manager
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
