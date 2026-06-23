package vpn.base.navigation

interface VpnnNavigationCommand

sealed class BaseNavigationCommand : VpnnNavigationCommand {
    data object Idle : BaseNavigationCommand()
    data object Back : BaseNavigationCommand()
}
