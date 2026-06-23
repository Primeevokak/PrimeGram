package vpn.tunnel

import kotlinx.coroutines.flow.StateFlow

interface VpnBackend<T : VpnConfig<Any>> {

    val tunnelState: StateFlow<VpnTunnelState>

    suspend fun setState(state: VpnTunnelState, config: T?)

    fun getStatistics(): VpnStatistics
}
