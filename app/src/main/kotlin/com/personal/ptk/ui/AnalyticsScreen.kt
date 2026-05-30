package com.personal.ptk.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.ptk.util.InboxAnalyzer
import com.personal.ptk.util.SenderMessage
import com.personal.ptk.util.SenderStats
import com.personal.ptk.util.ShizukuHelper
import com.personal.ptk.util.SmsRoleHelper
import com.personal.ptk.util.YearStats
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nf = remember { NumberFormat.getNumberInstance() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Top Senders", "By Year")

    var loading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var senderData = remember { mutableStateListOf<SenderStats>() }
    var yearData = remember { mutableStateListOf<YearStats>() }
    var loaded by remember { mutableStateOf(false) }

    val selectedSenders = remember { mutableStateListOf<String>() }
    val selectedYears = remember { mutableStateListOf<Int>() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteProgress by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        loading = true
        loaded = false
        selectedSenders.clear()
        selectedYears.clear()
        scope.launch {
            if (selectedTab == 0) {
                senderData.clear()
                val result = InboxAnalyzer.getTopSenders(context, limit = 100) { scanned, total ->
                    progressText = "Analyzing ${nf.format(scanned)} of ${nf.format(total)}..."
                }
                senderData.addAll(result)
            } else {
                yearData.clear()
                val result = InboxAnalyzer.getYearBreakdown(context) { scanned, total ->
                    progressText = "Analyzing ${nf.format(scanned)} of ${nf.format(total)}..."
                }
                yearData.addAll(result)
            }
            loading = false
            loaded = true
            progressText = null
        }
    }

    val selectedCount = if (selectedTab == 0) selectedSenders.size else selectedYears.size
    val selectedMsgCount = if (selectedTab == 0) {
        senderData.filter { it.sender in selectedSenders }.sumOf { it.count }
    } else {
        yearData.filter { it.year in selectedYears }.sumOf { it.total }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedCount > 0) {
                        Text("$selectedCount selected (${nf.format(selectedMsgCount)} msgs)")
                    } else {
                        Text("Inbox Analytics")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedCount > 0) {
                FloatingActionButton(
                    onClick = { showDeleteDialog = true },
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(Icons.Default.Delete, "Delete selected", tint = MaterialTheme.colorScheme.onError)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            loaded = false
                            selectedSenders.clear()
                            selectedYears.clear()
                        },
                        text = { Text(title) }
                    )
                }
            }

            if (!loaded && !loading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (selectedTab == 0) "Analyze your inbox to find senders with the most messages."
                        else "See message counts broken down by year.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { loadData() }) {
                        Text("Analyze Inbox")
                    }
                }
            } else if (loading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    progressText?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(senderData.toList(), key = { it.sender }) { stat ->
                        SenderRow(
                            stat = stat,
                            nf = nf,
                            checked = stat.sender in selectedSenders,
                            onCheckedChange = { checked ->
                                if (checked) selectedSenders.add(stat.sender)
                                else selectedSenders.remove(stat.sender)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(yearData.toList(), key = { it.year }) { stat ->
                        YearRow(
                            stat = stat,
                            nf = nf,
                            checked = stat.year in selectedYears,
                            onCheckedChange = { checked ->
                                if (checked) selectedYears.add(stat.year)
                                else selectedYears.remove(stat.year)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showDeleteDialog) {
        val isDefault = SmsRoleHelper.isDefaultSmsApp(context)
        val shizukuActive = ShizukuHelper.isActive(context)
        val canDelete = isDefault || shizukuActive

        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("Delete $selectedCount groups?") },
            text = {
                Column {
                    Text(
                        "Permanently delete ${nf.format(selectedMsgCount)} messages from your device. This cannot be undone."
                    )
                    if (!canDelete) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Requires PTK as default SMS app or Shizuku Power Mode.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    deleteProgress?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (deleting) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = true
                        scope.launch {
                            var totalDeleted = 0
                            if (selectedTab == 0) {
                                val targets = selectedSenders.toList()
                                for ((i, sender) in targets.withIndex()) {
                                    deleteProgress = "Deleting ${i + 1} of ${targets.size}: $sender..."
                                    val deleted = InboxAnalyzer.deleteBySender(context, sender)
                                    totalDeleted += deleted
                                    senderData.removeAll { it.sender == sender }
                                }
                                selectedSenders.clear()
                            } else {
                                val targets = selectedYears.sorted()
                                for ((i, year) in targets.withIndex()) {
                                    deleteProgress = "Deleting ${i + 1} of ${targets.size}: $year..."
                                    val deleted = InboxAnalyzer.deleteByYear(context, year)
                                    totalDeleted += deleted
                                    yearData.removeAll { it.year == year }
                                }
                                selectedYears.clear()
                            }
                            deleting = false
                            deleteProgress = null
                            showDeleteDialog = false
                            Toast.makeText(
                                context,
                                "Deleted ${nf.format(totalDeleted)} messages",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = canDelete && !deleting
                ) {
                    Text("Delete ${nf.format(selectedMsgCount)}", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !deleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SenderRow(
    stat: SenderStats,
    nf: NumberFormat,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var samples by remember { mutableStateOf<List<SenderMessage>?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        expanded = !expanded
                        if (expanded && samples == null) {
                            scope.launch {
                                samples = InboxAnalyzer.getSampleMessages(context, stat.sender, limit = 3)
                            }
                        }
                    }
                    .padding(top = 8.dp)
            ) {
                if (stat.contactName != null) {
                    Text(stat.contactName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stat.sender,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(stat.sender, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "${nf.format(stat.count)} msgs · ${stat.oldestYear}–${stat.newestYear}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    if (samples == null) {
                        Text("Loading...", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (samples!!.isEmpty()) {
                        Text("No previews available", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Recent messages:", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        samples!!.forEach { msg ->
                            Spacer(Modifier.height(3.dp))
                            Text(dateFormat.format(Date(msg.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(msg.body, style = MaterialTheme.typography.bodySmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearRow(
    stat: YearStats,
    nf: NumberFormat,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Column(modifier = Modifier.weight(1f)) {
                Text("${stat.year}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${nf.format(stat.smsCount)} SMS + ${nf.format(stat.mmsCount)} MMS = ${nf.format(stat.total)} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
