package com.personal.ptk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.personal.ptk.App
import com.personal.ptk.util.InboxPurger
import com.personal.ptk.util.InboxScanner
import com.personal.ptk.util.ShizukuHelper
import com.personal.ptk.util.SmsRoleHelper
import kotlinx.coroutines.launch

/**
 * The single, consolidated inbox-cleaning surface (shown on the main screen).
 *
 *  - "Scan Inbox" classifies every message. With the Auto-delete setting OFF
 *    (default) detected spam is vaulted for review and nothing is deleted;
 *    with it ON, spam is deleted immediately during the scan.
 *  - "Delete confirmed spam (N)" purges everything you've confirmed in the
 *    Vault (N = pending purge count).
 *
 * Deletion uses Shizuku silently when Power Mode is active; otherwise PTK is
 * temporarily made the default SMS app via the system role prompt, then the
 * user switches back from Settings.
 */
@Composable
fun InboxCleanupCard(
    onNavigateToVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    var autoDelete by remember {
        mutableStateOf(app.prefs.getBoolean(App.PREF_AUTO_DELETE, false))
    }
    var isDefault by remember { mutableStateOf(SmsRoleHelper.isDefaultSmsApp(context)) }
    var shizukuActive by remember { mutableStateOf(ShizukuHelper.isActive(context)) }

    var scanning by remember { mutableStateOf(false) }
    var scanProgressText by remember { mutableStateOf<String?>(null) }
    var scanResultText by remember { mutableStateOf<String?>(null) }
    var offerOpenVault by remember { mutableStateOf(false) }

    var purging by remember { mutableStateOf(false) }
    var purgeProgressText by remember { mutableStateOf<String?>(null) }
    var purgeResultText by remember { mutableStateOf<String?>(null) }

    var pendingAction by remember { mutableStateOf<String?>(null) }
    val pendingPurgeCount by app.database.vaultDao()
        .countPendingPurgeFlow().collectAsState(initial = 0)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                autoDelete = app.prefs.getBoolean(App.PREF_AUTO_DELETE, false)
                isDefault = SmsRoleHelper.isDefaultSmsApp(context)
                shizukuActive = ShizukuHelper.isActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun runScan(deleteFromInbox: Boolean) {
        scanning = true
        scanResultText = null
        scanProgressText = null
        offerOpenVault = false
        scope.launch {
            val doScan: suspend () -> Unit = {
                val result = InboxScanner.scan(
                    context,
                    deleteFromInbox = deleteFromInbox
                ) { current, total, killed ->
                    scanProgressText = "Scanning $current of $total ($killed spam found)"
                }
                val sb = StringBuilder()
                sb.append("Scanned ${result.scanned} messages. ")
                sb.append("Found ${result.killed} spam. ")
                if (deleteFromInbox) {
                    if (result.deleted > 0) sb.append("Deleted ${result.deleted} from inbox. ")
                    if (result.deleteFailed > 0) sb.append("\u26A0 ${result.deleteFailed} failed to delete! ")
                    sb.append("Inbox: ${result.inboxAfter} remaining.")
                } else {
                    sb.append("Saved to Vault for review.")
                    offerOpenVault = result.killed > 0
                }
                scanResultText = sb.toString()
            }
            if (deleteFromInbox && shizukuActive) {
                ShizukuHelper.withPtkAsDefault(context) { doScan() }
            } else {
                doScan()
            }
            scanning = false
            isDefault = SmsRoleHelper.isDefaultSmsApp(context)
        }
    }

    fun runPurge() {
        purging = true
        purgeResultText = null
        purgeProgressText = null
        scope.launch {
            val doPurge: suspend () -> Unit = {
                val result = InboxPurger.purge(
                    context,
                    useShizuku = shizukuActive
                ) { current, total ->
                    purgeProgressText = "Deleting $current of $total"
                }
                val sb = StringBuilder()
                sb.append("Attempted ${result.attempted}. ")
                sb.append("Deleted ${result.deleted}. ")
                if (result.notFound > 0) sb.append("${result.notFound} already gone. ")
                if (result.failed > 0) sb.append("\u26A0 ${result.failed} failed. ")
                sb.append("Inbox: ${result.inboxAfter} remaining.")
                purgeResultText = sb.toString()
            }
            if (shizukuActive) {
                ShizukuHelper.withPtkAsDefault(context) { doPurge() }
            } else {
                doPurge()
            }
            purging = false
            isDefault = SmsRoleHelper.isDefaultSmsApp(context)
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
                "scan_delete" -> runScan(deleteFromInbox = true)
                "purge" -> runPurge()
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Inbox Cleanup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (autoDelete)
                    "Auto-delete is ON. Scanning classifies every message and " +
                        "immediately deletes detected political spam from your inbox."
                else
                    "Scan classifies every message and saves detected spam to the " +
                        "Vault for review. Nothing is deleted until you confirm it.",
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
                        if (autoDelete) {
                            if (shizukuActive || isDefault) {
                                runScan(deleteFromInbox = true)
                            } else {
                                pendingAction = "scan_delete"
                                smsRoleLauncher.launch(
                                    SmsRoleHelper.makePtkDefaultIntent(context)
                                )
                            }
                        } else {
                            runScan(deleteFromInbox = false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            !autoDelete -> "Scan Inbox (Review First)"
                            shizukuActive || isDefault -> "Scan & Delete Spam"
                            else -> "Switch to PTK & Scan & Delete"
                        }
                    )
                }
            }

            scanResultText?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.contains("\u26A0")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                )
                if (offerOpenVault) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onNavigateToVault,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open Vault to Review") }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Pending: $pendingPurgeCount confirmed spam still in inbox",
                style = MaterialTheme.typography.bodySmall,
                color = if (pendingPurgeCount > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (purging) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp).padding(end = 12.dp)
                    )
                    Text(
                        purgeProgressText ?: "Starting...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        if (shizukuActive || isDefault) {
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
                        if (shizukuActive || isDefault)
                            "Delete confirmed spam ($pendingPurgeCount)"
                        else "Switch to PTK & Delete ($pendingPurgeCount)"
                    )
                }
            }

            purgeResultText?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.contains("\u26A0")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
