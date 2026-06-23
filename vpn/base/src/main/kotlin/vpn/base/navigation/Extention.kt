package vpn.base.navigation

import androidx.navigation.NavHostController

fun NavHostController.navigateBack() = this.popBackStack()
