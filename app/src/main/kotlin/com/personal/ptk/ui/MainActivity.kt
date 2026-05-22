package com.personal.ptk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.personal.ptk.App
import com.personal.ptk.ui.theme.PtkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as App
        val setupComplete = app.prefs.getBoolean(App.PREF_SETUP_COMPLETE, false)

        setContent {
            PtkTheme {
                val navController = rememberNavController()
                PtkNavHost(
                    navController = navController,
                    setupComplete = setupComplete
                )
            }
        }
    }
}
