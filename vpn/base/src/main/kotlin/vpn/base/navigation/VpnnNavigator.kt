package vpn.base.navigation

import kotlinx.coroutines.flow.MutableStateFlow

class VpnnNavigator {
    val navCommandFlow: MutableStateFlow<VpnnNavigationCommand> =
        MutableStateFlow(BaseNavigationCommand.Idle)

    fun onNavigated() {
        navCommandFlow.value = BaseNavigationCommand.Idle
    }

    fun sendNavigationCommand(command: VpnnNavigationCommand) {
        navCommandFlow.value = command
    }
}
