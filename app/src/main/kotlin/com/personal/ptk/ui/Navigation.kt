package com.personal.ptk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.personal.ptk.App
import com.personal.ptk.billing.SubscriptionState

enum class Screen(val route: String) {
    Setup("setup"),
    Main("main"),
    Rules("rules"),
    Vault("vault"),
    Settings("settings"),
    Analytics("analytics"),
    KillLog("killlog"),
    Help("help"),
    Paywall("paywall")
}

@Composable
fun PtkNavHost(
    navController: NavHostController,
    setupComplete: Boolean
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val subState by app.billingManager.state.collectAsState()

    val startDest = when {
        !setupComplete -> Screen.Setup.route
        !subState.hasAccess && subState !is SubscriptionState.Loading -> Screen.Paywall.route
        else -> Screen.Main.route
    }

    NavHost(
        navController = navController,
        startDestination = startDest
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
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
                onNavigateToKillLog = { navController.navigate(Screen.KillLog.route) },
                onNavigateToHelp = { navController.navigate(Screen.Help.route) }
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
        composable(Screen.Analytics.route) {
            AnalyticsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.KillLog.route) {
            KillLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Help.route) {
            SetupScreen(
                helpMode = true,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Paywall.route) {
            PaywallScreen()
        }
    }
}
