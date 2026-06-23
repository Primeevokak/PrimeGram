package vpn.tunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.backend.Tunnel.State

class VpnTunnel() : Tunnel {

    val tunnelState = MutableStateFlow(State.DOWN)

    override fun getName(): String = "vpn-tun"

    override fun onStateChange(newState: State) {
        tunnelState.update { newState }
    }

    override fun isIpv4ResolutionPreferred(): Boolean = true
}
