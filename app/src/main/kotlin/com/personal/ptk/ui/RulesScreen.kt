package com.personal.ptk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.ptk.App
import com.personal.ptk.classify.Classifier
import com.personal.ptk.classify.DefaultKeywords
import android.widget.Toast
import java.util.Locale
import com.personal.ptk.data.entities.RuleEntry
import com.personal.ptk.data.entities.RuleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Keywords", "Allowlist", "Blocklist")
    val ruleTypes = listOf(RuleType.KEYWORD, RuleType.ALLOWLIST_NUMBER, RuleType.BLOCKLIST_NUMBER)

    val rawRules by app.database.ruleDao().getByTypeFlow(ruleTypes[selectedTab])
        .collectAsState(initial = emptyList())
    val rules = remember(rawRules) { rawRules.sortedBy { it.value.lowercase() } }

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add rule")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleItem(
                        rule = rule,
                        showWarning = selectedTab == 0 && isDangerousKeyword(rule.value),
                        onDelete = {
                            scope.launch { app.database.ruleDao().deleteById(rule.id) }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            ruleType = ruleTypes[selectedTab],
            tabLabel = tabs[selectedTab],
            onDismiss = { showAddDialog = false },
            onAdd = { value ->
                scope.launch {
                    val type = ruleTypes[selectedTab]
                    val normalized = if (type != RuleType.KEYWORD) {
                        Classifier.normalizeNumber(value)
                    } else {
                        value.lowercase(Locale.ROOT).trim()
                    }
                    if (normalized.isNotBlank()) {
                        val already = app.database.ruleDao()
                            .countByTypeAndValue(type, normalized)
                        if (already == 0) {
                            app.database.ruleDao().insert(
                                RuleEntry(type = type, value = normalized)
                            )
                            // Adding to blocklist removes from allowlist (and vice versa)
                            if (type == RuleType.BLOCKLIST_NUMBER) {
                                app.database.ruleDao().deleteFromAllowlist(normalized)
                            } else if (type == RuleType.ALLOWLIST_NUMBER) {
                                app.database.ruleDao().deleteFromBlocklist(normalized)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "\"$normalized\" already exists",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun RuleItem(rule: RuleEntry, showWarning: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (showWarning) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Broad keyword",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(rule.value, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, "Delete")
        }
    }
}

@Composable
private fun AddRuleDialog(
    ruleType: RuleType,
    tabLabel: String,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to $tabLabel") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = {
                    Text(
                        if (ruleType == RuleType.KEYWORD) "Keyword or phrase"
                        else "Phone number"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun isDangerousKeyword(keyword: String): Boolean {
    val k = keyword.lowercase().trim()
    return k.length < 3 || k in DefaultKeywords.COMMON_STOPWORDS
}
