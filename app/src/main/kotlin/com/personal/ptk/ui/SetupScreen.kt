package com.personal.ptk.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.ptk.App

private const val TOTAL_STEPS = 4

/**
 * Doubles as the first-run permission wizard (helpMode = false) and a
 * re-openable, browsable "Setup & Help" guide (helpMode = true) reachable
 * from the main screen.
 */
@Composable
fun SetupScreen(
    helpMode: Boolean = false,
    onSetupComplete: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    if (helpMode) {
        HelpGuide(onBack = onBack)
    } else {
        SetupWizard(onSetupComplete = onSetupComplete)
    }
}

@Composable
private fun SetupWizard(onSetupComplete: () -> Unit) {
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
                        "the default app to PTK and back).\n\n" +
                        "Want fully silent, hands-off deletion? Open Setup & Help from the " +
                        "main screen for the Shizuku and Automate tiers."
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpGuide(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup & Help") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Three tiers of protection. Each builds on the one before it \u2014 " +
                    "start with Basic; add Shizuku and Automate only if you want " +
                    "fully hands-off, silent deletion.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HelpTier(
                title = "Level 1 \u2014 Basic protection",
                body = "Works with just four permissions, no extra apps:\n\n" +
                    "1. SMS access \u2014 read incoming texts to classify them.\n" +
                    "2. Contacts \u2014 messages from people you know are never touched.\n" +
                    "3. Notification access \u2014 cancel Google Messages notifications " +
                    "for killed spam.\n" +
                    "4. Post notifications \u2014 alert you if permissions are lost after " +
                    "a reboot.\n\n" +
                    "Google Messages stays your default SMS app. PTK silences spam " +
                    "notifications in real time. To remove spam from the inbox, use " +
                    "Inbox Cleanup > Scan Inbox on the main screen \u2014 it briefly " +
                    "switches the default SMS app to PTK and back (you'll see a system " +
                    "prompt), then you switch back from Settings.",
                initiallyExpanded = true
            )

            HelpTier(
                title = "Level 2 \u2014 Silent deletion (Shizuku)",
                body = "Shizuku lets PTK delete spam silently in the background \u2014 no " +
                    "prompts, and without permanently changing your default SMS app.\n\n" +
                    "1. Install Shizuku from github.com/RikkaApps/Shizuku/releases.\n" +
                    "2. Start Shizuku via wireless debugging: enable Developer Options > " +
                    "Wireless debugging, pair your phone, then start Shizuku.\n" +
                    "3. In PTK Settings > Shizuku Power Mode, tap Grant Permission, " +
                    "then turn on Enable Power Mode.\n\n" +
                    "Once active, detected spam is deleted in real time and the Inbox " +
                    "Cleanup actions run with zero confirmation dialogs."
            )

            HelpTier(
                title = "Level 3 \u2014 Hands-off (Automate keeper)",
                body = "Shizuku stops running after a reboot (a non-root limitation). To " +
                    "keep it alive automatically:\n\n" +
                    "1. Grant WRITE_SECURE_SETTINGS once via adb \u2014 see the exact " +
                    "command under Settings > Shizuku Power Mode > Auto-Start Setup Guide.\n" +
                    "2. Install Automate (LlamaLab) and import a Shizuku keeper flow that " +
                    "restarts Shizuku via wireless ADB on boot.\n" +
                    "3. Set Shizuku, Automate, and PoliticalTextKiller to Unrestricted " +
                    "battery usage so the system doesn't kill them.\n\n" +
                    "With this in place, Power Mode survives reboots and keeps working " +
                    "untouched."
            )
        }
    }
}

@Composable
private fun HelpTier(
    title: String,
    body: String,
    initiallyExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
