package com.personal.ptk.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.ptk.App

private const val TOTAL_STEPS = 4

@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    var step by remember { mutableIntStateOf(0) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) step = 1
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) step = 2
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { step = TOTAL_STEPS }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (step < TOTAL_STEPS) Icons.Default.Sms else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(64.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = when (step) {
                    0 -> "Step 1 of $TOTAL_STEPS: SMS Permissions"
                    1 -> "Step 2 of $TOTAL_STEPS: Contacts Access"
                    2 -> "Step 3 of $TOTAL_STEPS: Notification Access"
                    3 -> "Step 4 of $TOTAL_STEPS: Post Notifications"
                    else -> "You're Protected"
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = when (step) {
                    0 -> "PoliticalTextKiller needs SMS permissions to read incoming texts and classify them."
                    1 -> "Contact access ensures messages from people you know are never blocked."
                    2 -> "Notification Access lets us cancel Google Messages notifications for killed spam."
                    3 -> "Allow notifications so we can alert you if permissions are lost after a reboot."
                    else -> "PoliticalTextKiller is active. Google Messages stays as your default " +
                        "SMS app. PTK silences spam notifications in real time and can clean " +
                        "your inbox on demand via Scan Inbox (which will temporarily switch " +
                        "the default app to PTK and back)."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    when (step) {
                        0 -> smsLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                            )
                        )
                        1 -> contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                        2 -> {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                            step = 3
                        }
                        3 -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                step = TOTAL_STEPS
                            }
                        }
                        else -> {
                            app.prefs.edit()
                                .putBoolean(App.PREF_SETUP_COMPLETE, true)
                                .apply()
                            onSetupComplete()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (step) {
                        0 -> "Grant SMS Permissions"
                        1 -> "Grant Contacts Access"
                        2 -> "Open Notification Settings"
                        3 -> "Grant Notification Permission"
                        else -> "Get Started"
                    }
                )
            }

            if (step in 1..3) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { step++ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Skip")
                }
            }
        }
    }
}
