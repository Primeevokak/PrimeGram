package vpn.tunnel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config

class AwgBackendAdapter(
    private val goBackend: GoBackend,
    private val vpnTunnel: VpnTunnel,
) : VpnBackend<VpnConfig.Awg> {

    companion object {
        private const val MAX_ATTEMPTS = 4
        private const val RETRY_DELAY_MS = 2000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tunnelState = MutableStateFlow(VpnTunnelState.DOWN)
    override val tunnelState: StateFlow<VpnTunnelState> = _tunnelState.asStateFlow()

    init {
        scope.launch {
            vpnTunnel.tunnelState.collect { awgState ->
                _tunnelState.value = mapAwgStateToVpnState(awgState)
            }
        }
    }

    /**
     * Внутри [org.amnezia.awg.backend.GoBackend.setStateInternal] стартует сервис.
     * При первой попытке подключиться он не запущен и выбрасывается исключение.
     * По этой причине делается несколько попыток в цикле.
     */
    override suspend fun setState(state: VpnTunnelState, config: VpnConfig.Awg?) {
        if (state == VpnTunnelState.CONNECTING) {
            _tunnelState.value = VpnTunnelState.CONNECTING
            return
        }

        val awgState = mapVpnStateToAwgState(state)
        val awgConfig = config?.config as? Config

        if (state == VpnTunnelState.UP) {
            _tunnelState.value = VpnTunnelState.CONNECTING
        }

        var attempt = 0
        while (attempt <= MAX_ATTEMPTS) {
            try {
                goBackend.setState(vpnTunnel, awgState, awgConfig)
                return
            } catch (_: Exception) {
                attempt++
                if (attempt > MAX_ATTEMPTS) {
                    _tunnelState.value = VpnTunnelState.DOWN
                } else {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    override fun getStatistics(): VpnStatistics {
        val stats = goBackend.getStatistics(vpnTunnel)
        return VpnStatistics(
            totalRxBytes = stats.totalRx(),
            totalTxBytes = stats.totalTx(),
        )
    }

    private fun mapAwgStateToVpnState(awgState: Tunnel.State): VpnTunnelState {
        return when (awgState) {
            Tunnel.State.UP -> VpnTunnelState.UP
            Tunnel.State.DOWN -> VpnTunnelState.DOWN
        }
    }

    private fun mapVpnStateToAwgState(vpnState: VpnTunnelState): Tunnel.State {
        return when (vpnState) {
            VpnTunnelState.UP -> Tunnel.State.UP
            VpnTunnelState.CONNECTING -> Tunnel.State.UP
            VpnTunnelState.DOWN -> Tunnel.State.DOWN
        }
    }
}
