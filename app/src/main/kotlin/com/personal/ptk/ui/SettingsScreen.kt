package com.personal.ptk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.personal.ptk.App
import com.personal.ptk.util.InboxPurger
import com.personal.ptk.util.InboxScanner
import com.personal.ptk.util.SmsRoleHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App

    var mlEnabled by remember {
        mutableStateOf(app.prefs.getBoolean(App.PREF_ML_ENABLED, false))
    }
    var nuclearMode by remember {
        mutableStateOf(app.prefs.getBoolean(App.PREF_NUCLEAR_MODE, false))
    }
    var retentionDays by remember {
        mutableFloatStateOf(app.prefs.getInt(App.PREF_RETENTION_DAYS, 90).toFloat())
    }
    var scanning by remember { mutableStateOf(false) }
    var scanProgressText by remember { mutableStateOf<String?>(null) }
    var scanResultText by remember { mutableStateOf<String?>(null) }
    var purgeResultText by remember { mutableStateOf<String?>(null) }
    var purgeProgressText by remember { mutableStateOf<String?>(null) }
    var purging by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var isDefault by remember { mutableStateOf(SmsRoleHelper.isDefaultSmsApp(context)) }
    val pendingPurgeCount by app.database.vaultDao()
        .countPendingPurgeFlow().collectAsState(initial = 0)
    val scope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefault = SmsRoleHelper.isDefaultSmsApp(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun runScan() {
        scanning = true
        scanResultText = null
        scanProgressText = null
        scope.launch {
            val result = InboxScanner.scan(context) { current, total, killed ->
                scanProgressText = "Scanning $current of $total ($killed spam found)"
            }
            val sb = StringBuilder()
            sb.append("Scanned ${result.scanned} messages. ")
            sb.append("Found ${result.killed} spam. ")
            if (result.deleted > 0) {
                sb.append("Deleted ${result.deleted} from inbox. ")
            }
            if (result.deleteFailed > 0) {
                sb.append("⚠ ${result.deleteFailed} failed to delete! ")
            }
            sb.append("Inbox: ${result.inboxAfter} remaining.")
            scanResultText = sb.toString()
            scanning = false
        }
    }

    fun runPurge() {
        purging = true
        purgeResultText = null
        purgeProgressText = null
        scope.launch {
            val result = InboxPurger.purge(context) { current, total ->
                purgeProgressText = "Purging $current of $total"
            }
            val sb = StringBuilder()
            sb.append("Attempted ${result.attempted}. ")
            sb.append("Deleted ${result.deleted}. ")
            if (result.notFound > 0) sb.append("${result.notFound} not found. ")
            if (result.failed > 0) sb.append("⚠ ${result.failed} failed. ")
            sb.append("Inbox: ${result.inboxAfter} remaining.")
            purgeResultText = sb.toString()
            purging = false
        }
    }

    val smsRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = SmsRoleHelper.isDefaultSmsApp(context)
        val action = pendingAction
        pendingAction = null
        if (isDefault) {
            when (action) {
                "scan" -> runScan()
                "purge" -> runPurge()
            }
        }
    }

    /* switchBack uses startActivity directly; lifecycle observer refreshes state on resume */

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
                        title = "ML Classifier",
                        subtitle = "Use on-device machine learning model for enhanced detection. " +
                            "Requires a trained model in app assets.",
                        checked = mlEnabled,
                        onCheckedChange = {
                            mlEnabled = it
                            app.prefs.edit().putBoolean(App.PREF_ML_ENABLED, it).apply()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingToggle(
                        title = "Nuclear Shortcode Mode",
                        subtitle = "Kill ANY message from a non-contact 5-6 digit shortcode " +
                            "that contains a dollar sign. Aggressive but effective.",
                        checked = nuclearMode,
                        onCheckedChange = {
                            nuclearMode = it
                            app.prefs.edit().putBoolean(App.PREF_NUCLEAR_MODE, it).apply()
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
                    Text("Scan Inbox", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Classifies every SMS already in your inbox and deletes the " +
                            "political spam. PTK will temporarily become your default " +
                            "SMS app (Android requires this to delete messages). " +
                            "After the scan you can switch back to Google Messages above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    if (scanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(24.dp).padding(end = 12.dp)
                            )
                            Text(
                                scanProgressText ?: "Starting scan...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isDefault) {
                                    runScan()
                                } else {
                                    pendingAction = "scan"
                                    smsRoleLauncher.launch(
                                        SmsRoleHelper.makePtkDefaultIntent(context)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isDefault) "Scan Inbox Now"
                                else "Switch to PTK & Scan Inbox"
                            )
                        }
                    }

                    scanResultText?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it.contains("⚠")) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Purge Spam from Inbox",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Every time PTK catches a spam SMS in real time, it records the " +
                            "row ID. Tap to delete every recorded spam from your inbox in " +
                            "one shot. PTK will become default temporarily.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pending: $pendingPurgeCount message(s) flagged but still in inbox",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pendingPurgeCount > 0)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    if (purging) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(24.dp).padding(end = 12.dp)
                            )
                            Text(
                                purgeProgressText ?: "Starting purge...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isDefault) {
                                    runPurge()
                                } else {
                                    pendingAction = "purge"
                                    smsRoleLauncher.launch(
                                        SmsRoleHelper.makePtkDefaultIntent(context)
                                    )
                                }
                            },
                            enabled = pendingPurgeCount > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isDefault) "Purge $pendingPurgeCount Spam Now"
                                else "Switch to PTK & Purge $pendingPurgeCount Spam"
                            )
                        }
                    }

                    purgeResultText?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it.contains("⚠")) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                        )
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
            Text(
                "Personal use. No data leaves this device.",
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
    onCheckedChange: (Boolean) -> Unit
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
