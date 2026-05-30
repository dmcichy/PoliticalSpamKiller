package com.personal.ptk.ui

import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.collectAsState
import com.personal.ptk.App
import com.personal.ptk.billing.SubscriptionState
import com.personal.ptk.util.ShizukuHelper
import com.personal.ptk.util.SmsRoleHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App

    var mlEnabled by remember {
        mutableStateOf(app.prefs.getBoolean(App.PREF_ML_ENABLED, false))
    }
    var autoDelete by remember {
        mutableStateOf(app.prefs.getBoolean(App.PREF_AUTO_DELETE, false))
    }
    var retentionDays by remember {
        mutableFloatStateOf(app.prefs.getInt(App.PREF_RETENTION_DAYS, 90).toFloat())
    }
    var isDefault by remember { mutableStateOf(SmsRoleHelper.isDefaultSmsApp(context)) }

    var shizukuEnabled by remember {
        mutableStateOf(app.prefs.getBoolean(App.PREF_SHIZUKU_ENABLED, false))
    }
    var shizukuInstalled by remember { mutableStateOf(ShizukuHelper.isInstalled(context)) }
    var shizukuRunning by remember { mutableStateOf(ShizukuHelper.isRunning()) }
    var shizukuPermission by remember { mutableStateOf(ShizukuHelper.isPermissionGranted()) }
    val shizukuActive = shizukuEnabled && shizukuRunning && shizukuPermission

    var showAutoStartGuide by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefault = SmsRoleHelper.isDefaultSmsApp(context)
                shizukuInstalled = ShizukuHelper.isInstalled(context)
                shizukuRunning = ShizukuHelper.isRunning()
                shizukuPermission = ShizukuHelper.isPermissionGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingToggle(
                        title = "Auto-delete detected spam (skip review)",
                        subtitle = "When on, \"Scan Inbox\" deletes detected political " +
                            "spam immediately instead of saving it to the Vault for " +
                            "review. Leave off until you trust the classifier.",
                        checked = autoDelete,
                        onCheckedChange = {
                            autoDelete = it
                            app.prefs.edit().putBoolean(App.PREF_AUTO_DELETE, it).apply()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingToggle(
                        title = "ML Classifier",
                        subtitle = "On-device spam detection using a trained TFLite model. " +
                            "Messages scoring >= 0.90 are flagged as political spam.",
                        checked = mlEnabled,
                        enabled = true,
                        onCheckedChange = {
                            mlEnabled = it
                            app.prefs.edit().putBoolean(App.PREF_ML_ENABLED, it).apply()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        "Vault Retention",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Keep killed messages for ${retentionDays.toInt()} days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = retentionDays,
                        onValueChange = { retentionDays = it },
                        onValueChangeFinished = {
                            app.prefs.edit()
                                .putInt(App.PREF_RETENTION_DAYS, retentionDays.toInt())
                                .apply()
                        },
                        valueRange = 7f..365f,
                        steps = 0
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default SMS App", style = MaterialTheme.typography.titleSmall)
                    val defaultPkg = SmsRoleHelper.currentDefaultPackage(context)
                    val defaultLabel = defaultPkg?.let { SmsRoleHelper.appLabel(context, it) }
                        ?: "(unknown)"
                    Text(
                        if (isDefault) "PTK is currently the default SMS app."
                        else "Current: $defaultLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDefault) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isDefault) {
                        Spacer(Modifier.height(8.dp))
                        val prevLabel = SmsRoleHelper.previousAppLabel(context)
                        Text(
                            "RCS and MMS won't work normally until you switch back.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { SmsRoleHelper.launchSwitchBack(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Switch back to $prevLabel")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Shizuku Power Mode",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Enables fully silent SMS deletion without switching your " +
                            "default SMS app. Requires the Shizuku app to be installed " +
                            "and running.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Installed", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (shizukuInstalled) "Yes" else "No",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (shizukuInstalled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Running", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (shizukuRunning) "Yes" else "No",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (shizukuRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permission", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (shizukuPermission) "Granted" else "Not Granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (shizukuPermission) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (!shizukuInstalled) {
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Install Shizuku") }
                    } else if (shizukuRunning && !shizukuPermission) {
                        Button(
                            onClick = {
                                ShizukuHelper.requestPermission()
                                shizukuPermission = ShizukuHelper.isPermissionGranted()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Grant Permission") }
                    } else if (!shizukuRunning) {
                        Button(
                            onClick = {
                                try {
                                    val intent = context.packageManager
                                        .getLaunchIntentForPackage(
                                            "moe.shizuku.privileged.api"
                                        )
                                    if (intent != null) context.startActivity(intent)
                                } catch (_: Exception) { }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open Shizuku") }
                    }

                    if (shizukuInstalled && shizukuRunning && shizukuPermission) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingToggle(
                            title = "Enable Power Mode",
                            subtitle = if (shizukuActive)
                                "Active. Real-time spam deletion and one-tap " +
                                    "scan/purge with zero confirmation dialogs."
                            else "Toggle on to enable silent background SMS deletion.",
                            checked = shizukuEnabled,
                            onCheckedChange = {
                                shizukuEnabled = it
                                app.prefs.edit()
                                    .putBoolean(App.PREF_SHIZUKU_ENABLED, it).apply()
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAutoStartGuide = !showAutoStartGuide },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Auto-Start Setup Guide",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (showAutoStartGuide) Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,
                            contentDescription = "Toggle guide"
                        )
                    }
                    AnimatedVisibility(visible = showAutoStartGuide) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "To have Shizuku start automatically after reboot " +
                                    "(without root):\n\n" +
                                    "1. Install the thedjchi Shizuku fork from:\n" +
                                    "   github.com/thedjchi/Shizuku\n\n" +
                                    "2. Connect phone to PC and run:\n" +
                                    "   adb shell pm grant " +
                                    "moe.shizuku.privileged.api " +
                                    "android.permission.WRITE_SECURE_SETTINGS\n\n" +
                                    "3. In Shizuku app settings, enable\n" +
                                    "   \"Start on boot (wireless ADB)\"\n\n" +
                                    "4. After reboot, Shizuku starts in ~5 seconds\n" +
                                    "   (requires Wi-Fi to be connected).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val subState by app.billingManager.state.collectAsState()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Subscription", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    val statusText = when (subState) {
                        is SubscriptionState.Loading -> "Checking..."
                        is SubscriptionState.Trial -> {
                            val days = app.billingManager.trialDaysLeft()
                            "Free trial — $days day${if (days != 1L) "s" else ""} remaining"
                        }
                        is SubscriptionState.Active -> "Active subscription"
                        is SubscriptionState.Grace -> "Grace period — please update payment"
                        is SubscriptionState.Expired -> "Expired — subscribe to continue"
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (subState) {
                            is SubscriptionState.Active -> MaterialTheme.colorScheme.primary
                            is SubscriptionState.Expired -> MaterialTheme.colorScheme.error
                            is SubscriptionState.Grace -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://play.google.com/store/account/subscriptions" +
                                        "?sku=ptk_monthly&package=com.personal.ptk"
                                )
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manage Subscription")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Privacy Policy", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "All classification happens on-device. " +
                            "No message content is ever transmitted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://dmc-inc.com/privacy"
                                )
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Privacy Policy")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "PoliticalTextKiller v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
