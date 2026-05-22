package com.personal.ptk.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

enum class Screen(val route: String) {
    Setup("setup"),
    Main("main"),
    Rules("rules"),
    Vault("vault"),
    Settings("settings")
}

@Composable
fun PtkNavHost(
    navController: NavHostController,
    setupComplete: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (setupComplete) Screen.Main.route else Screen.Setup.route
    ) {
        composable(Screen.Setup.route) {
            SetupScreen(onSetupComplete = {
                navController.navigate(Screen.Main.route) {
                    popUpTo(Screen.Setup.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToRules = { navController.navigate(Screen.Rules.route) },
                onNavigateToVault = { navController.navigate(Screen.Vault.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Rules.route) {
            RulesScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Vault.route) {
            VaultScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
