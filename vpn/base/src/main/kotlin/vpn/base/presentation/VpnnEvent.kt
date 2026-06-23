package vpn.base.presentation

@Deprecated("Use VpnnEffect instead")
interface VpnnEvent

sealed class BaseEvent : VpnnEvent {
    data object None : BaseEvent()
}
