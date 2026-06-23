package vpn.base.presentation

interface VpnnAction

sealed class BaseAction : VpnnAction {
    data object NavigateBack : BaseAction()
}
