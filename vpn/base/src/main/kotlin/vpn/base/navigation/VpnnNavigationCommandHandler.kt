package vpn.base.navigation

import androidx.annotation.CallSuper
import androidx.navigation.NavHostController

open class VpnnNavigationCommandHandler(
    private val navController: NavHostController,
    private val navigator: VpnnNavigator,
) {

    @CallSuper
    open fun handleCommand(command: VpnnNavigationCommand) {
        navigator.onNavigated()
    }

    protected fun handleBaseCommand(command: VpnnNavigationCommand) {
        when (command) {
            BaseNavigationCommand.Back -> navController.navigateBack()
        }
    }
}
