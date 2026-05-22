package com.personal.ptk.ui

import android.app.Activity
import android.content.ContentValues
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.ptk.App
import com.personal.ptk.classify.Classifier
import com.personal.ptk.data.entities.RuleEntry
import com.personal.ptk.data.entities.RuleType
import com.personal.ptk.data.entities.VaultEntry
import com.personal.ptk.util.CsvExporter
import com.personal.ptk.util.SmsRoleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    val entries by app.database.vaultDao().getAllFlow().collectAsState(initial = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }
    val lastToast = remember { mutableStateOf<Toast?>(null) }
    fun showToast(msg: String) {
        lastToast.value?.cancel()
        val t = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
        lastToast.value = t
        t.show()
    }

    var isDefault by remember { mutableStateOf(SmsRoleHelper.isDefaultSmsApp(context)) }
    
    var pendingRestore by remember { mutableStateOf<VaultEntry?>(null) }

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

    val smsRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = SmsRoleHelper.isDefaultSmsApp(context)
        val toRestore = pendingRestore
        pendingRestore = null
        if (isDefault && toRestore != null) {
            scope.launch {
                restoreToInbox(context, toRestore)
                app.database.vaultDao().deleteById(toRestore.id)
                showToast("Restored to inbox")
            }
        } else if (toRestore != null) {
            showToast("Restore requires PTK to be the default SMS app")
        }
    }

    /* switchBack uses startActivity directly; lifecycle observer refreshes state on resume */

    fun handleRestore(entry: VaultEntry) {
        if (isDefault) {
            scope.launch {
                restoreToInbox(context, entry)
                app.database.vaultDao().deleteById(entry.id)
                showToast("Restored to inbox")
            }
        } else {
            pendingRestore = entry
            smsRoleLauncher.launch(SmsRoleHelper.makePtkDefaultIntent(context))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spam Vault (${entries.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Clear vault")
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            CsvExporter.exportAndShare(context, app.database.vaultDao())
                        }
                    }) {
                        Icon(Icons.Default.Share, "Export CSV")
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "No killed messages yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "When PoliticalTextKiller blocks a spam text, it will appear here for review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDefault) {
                    item {
                        val prevLabel = SmsRoleHelper.previousAppLabel(context)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "PTK is currently your default SMS app",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "RCS/MMS won't work normally until you switch back.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
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
                }

                items(entries, key = { it.id }) { entry ->
                    VaultItem(
                        entry = entry,
                        onRestore = { handleRestore(entry) },
                        onAllowSender = {
                            scope.launch {
                                val normalized = Classifier.normalizeNumber(entry.sender)
                                val already = app.database.ruleDao()
                                    .countByTypeAndValue(RuleType.ALLOWLIST_NUMBER, normalized)
                                if (already == 0) {
                                    app.database.ruleDao().insert(
                                        RuleEntry(
                                            type = RuleType.ALLOWLIST_NUMBER,
                                            value = normalized
                                        )
                                    )
                                }
                                withContext(Dispatchers.Main) {
                                    showToast(
                                        if (already == 0) "${entry.sender} added to allowlist"
                                        else "${entry.sender} already on allowlist"
                                    )
                                }
                            }
                        },
                        onDeleteByKeyword = {
                            scope.launch {
                                val count = app.database.vaultDao()
                                    .deleteByReasonAndRule(entry.reason, entry.matchedRule)
                                withContext(Dispatchers.Main) {
                                    showToast("Deleted $count entries matching ${entry.reason}: ${entry.matchedRule}")
                                }
                            }
                        },
                        onDeleteByNumber = {
                            scope.launch {
                                val normalizedSender = Classifier.normalizeNumber(entry.sender)
                                val allEntries = app.database.vaultDao().getAll()
                                val matchingIds = allEntries
                                    .filter { Classifier.normalizeNumber(it.sender) == normalizedSender }
                                    .map { it.id }
                                val count = if (matchingIds.isNotEmpty()) {
                                    app.database.vaultDao().deleteByIds(matchingIds)
                                } else 0
                                withContext(Dispatchers.Main) {
                                    showToast("Deleted $count entries from ${entry.sender}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Vault") },
            text = {
                Text(
                    "Permanently delete all ${entries.size} entries from the vault? " +
                        "This does NOT affect your SMS inbox — only the app's local log."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.database.vaultDao().deleteAll()
                        showClearDialog = false
                    }
                }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VaultItem(
    entry: VaultEntry,
    onRestore: () -> Unit,
    onAllowSender: () -> Unit,
    onDeleteByKeyword: () -> Unit,
    onDeleteByNumber: () -> Unit
) {
    val dateFormat = remember {
        SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
    }
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    entry.sender,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    dateFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    entry.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "${entry.reason}: ${entry.matchedRule}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRestore) { Text("Restore") }
                TextButton(onClick = onAllowSender) { Text("Allow Sender") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDeleteByKeyword) {
                    Text("Delete All: ${entry.matchedRule}", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDeleteByNumber) {
                    Text("Delete All: ${entry.sender}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private suspend fun restoreToInbox(context: android.content.Context, entry: VaultEntry) {
    withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, entry.sender)
                put(Telephony.Sms.BODY, entry.body)
                put(Telephony.Sms.DATE, entry.timestamp)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.READ, 0)
            }
            context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Could not restore message", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
