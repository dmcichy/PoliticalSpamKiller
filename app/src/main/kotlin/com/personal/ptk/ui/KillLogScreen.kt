package com.personal.ptk.ui

import android.content.ContentValues
import android.provider.Telephony
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.ptk.App
import com.personal.ptk.classify.Classifier
import com.personal.ptk.data.entities.RuleType
import com.personal.ptk.data.entities.VaultEntry
import com.personal.ptk.util.ShizukuHelper
import com.personal.ptk.util.SmsRoleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KillLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    val killed by app.database.vaultDao().getKilledFlow().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<VaultEntry?>(null) }

    val lastToast = remember { mutableStateOf<Toast?>(null) }
    fun showToast(msg: String) {
        lastToast.value?.cancel()
        val t = Toast.makeText(context, msg, Toast.LENGTH_SHORT)
        lastToast.value = t
        t.show()
    }

    fun doRestore(entry: VaultEntry) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val inserted = if (ShizukuHelper.isActive(app)) {
                    ShizukuHelper.silentInsertSms(entry.sender, entry.body, entry.timestamp)
                } else if (SmsRoleHelper.isDefaultSmsApp(context)) {
                    insertViaResolver(context, entry)
                } else {
                    false
                }
                if (inserted) {
                    // Undo the auto-block so this sender isn't killed again,
                    // and allowlist them since the user says this was wanted.
                    val normalized = Classifier.normalizeNumber(entry.sender)
                    val ruleDao = app.database.ruleDao()
                    ruleDao.deleteFromBlocklist(normalized)
                    if (ruleDao.countByTypeAndValue(RuleType.ALLOWLIST_NUMBER, normalized) == 0) {
                        ruleDao.insert(
                            com.personal.ptk.data.entities.RuleEntry(
                                type = RuleType.ALLOWLIST_NUMBER,
                                value = normalized
                            )
                        )
                    }
                    app.database.vaultDao().deleteById(entry.id)
                }
                inserted
            }
            showToast(
                if (ok) "Restored to inbox & allowlisted ${entry.sender}"
                else "Restore needs Shizuku Power Mode running"
            )
        }
    }

    val filtered = remember(killed, query) {
        if (query.isBlank()) killed
        else killed.filter {
            it.sender.contains(query, ignoreCase = true) ||
                it.body.contains(query, ignoreCase = true) ||
                it.matchedRule.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Kill Log (${killed.size})") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("Search sender, text, or rule") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        }
                    },
                    singleLine = true
                )
            }
        }
    ) { padding ->
        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (killed.isEmpty()) "Nothing deleted yet."
                    else "No matches for \"$query\".",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (killed.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Every message Power Mode deletes from your inbox is logged here so you can verify nothing important was removed — and restore it if it was.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { entry ->
                    KillLogItem(entry = entry, onRestore = { pendingRestore = entry })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    pendingRestore?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore message?") },
            text = {
                Text(
                    "This will put the message from ${entry.sender} back into your SMS inbox, " +
                        "remove that sender from the blocklist, and add them to the allowlist so " +
                        "they're never killed again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    doRestore(entry)
                    pendingRestore = null
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun KillLogItem(entry: VaultEntry, onRestore: () -> Unit) {
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
                Text(entry.sender, style = MaterialTheme.typography.titleSmall)
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
                TextButton(onClick = onRestore) { Text("Restore to inbox") }
            }
        }
    }
}

private fun insertViaResolver(context: android.content.Context, entry: VaultEntry): Boolean {
    return try {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, entry.sender)
            put(Telephony.Sms.BODY, entry.body)
            put(Telephony.Sms.DATE, entry.timestamp)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.READ, 1)
        }
        context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) != null
    } catch (_: Exception) {
        false
    }
}
