package vpn.tunnel

sealed class VpnConfig<out T : Any> {

    data class Awg(val config: Any) : VpnConfig<Any>()
}
