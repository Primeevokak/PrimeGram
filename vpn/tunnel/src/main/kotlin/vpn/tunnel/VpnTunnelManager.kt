package vpn.tunnel

import kotlinx.coroutines.flow.StateFlow

interface VpnTunnelManager<T : VpnConfig<Any>> {

    val tunnelState: StateFlow<VpnTunnelState>

    fun setState(state: VpnTunnelState, config: T?)

    fun getStatistics(): VpnStatistics
}
