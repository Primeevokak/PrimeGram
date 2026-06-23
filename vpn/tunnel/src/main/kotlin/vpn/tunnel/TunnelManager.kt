package vpn.tunnel

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TunnelManager(
    private val backend: VpnBackend<VpnConfig.Awg>,
) : VpnTunnelManager<VpnConfig.Awg> {

    companion object {
        private const val TAG = "TunnelManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val tunnelState: StateFlow<VpnTunnelState> = backend.tunnelState

    override fun setState(state: VpnTunnelState, config: VpnConfig.Awg?) {
        if (state == VpnTunnelState.UP && config == null) return
        scope.launch {
            try {
                backend.setState(state, config)
            } catch (e: Exception) {
                Log.e(TAG, "Error while setting tunnel state", e)
            }
        }
    }

    override fun getStatistics(): VpnStatistics = backend.getStatistics()
}
