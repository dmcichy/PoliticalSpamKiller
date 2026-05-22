package com.personal.ptk.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.ptk.App
import java.text.NumberFormat
import com.personal.ptk.util.KeywordSuggester
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToRules: () -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()

    var killSwitchOn by remember {
        mutableStateOf(app.prefs.getBoolean(App.PREF_KILL_SWITCH, true))
    }
    var killedToday by remember { mutableIntStateOf(0) }
    var killedWeek by remember { mutableIntStateOf(0) }
    var killedAll by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekStart = todayStart - 6 * 24 * 60 * 60 * 1000L

        killedToday = app.database.vaultDao().countSince(todayStart)
        killedWeek = app.database.vaultDao().countSince(weekStart)
        killedAll = app.database.vaultDao().countAll()

        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
        val bodies = app.database.vaultDao().getBodiesSince(thirtyDaysAgo)
        val activeKeywords = app.database.ruleDao().getAllActiveKeywords()
        suggestions = KeywordSuggester.suggest(bodies, activeKeywords.toSet())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PoliticalTextKiller") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Kill switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (killSwitchOn)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (killSwitchOn) "Protection ON" else "Protection OFF",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (killSwitchOn) "Political spam is being silenced"
                            else "All messages are passing through",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = killSwitchOn,
                        onCheckedChange = {
                            killSwitchOn = it
                            app.prefs.edit().putBoolean(App.PREF_KILL_SWITCH, it).apply()
                        }
                    )
                }
            }

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard("Today", killedToday, Modifier.weight(1f))
                StatCard("This Week", killedWeek, Modifier.weight(1f))
                StatCard("All Time", killedAll, Modifier.weight(1f))
            }

            // Suggested keywords (v1.1)
            if (suggestions.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Suggested Keywords",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Frequently seen in killed messages but not yet in your keyword list:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        suggestions.take(5).forEach { word ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(word, style = MaterialTheme.typography.bodyLarge)
                                TextButton(onClick = {
                                    scope.launch {
                                        val dao = app.database.ruleDao()
                                        if (dao.countByTypeAndValue(
                                                com.personal.ptk.data.entities.RuleType.KEYWORD, word
                                            ) == 0
                                        ) {
                                            dao.insert(
                                                com.personal.ptk.data.entities.RuleEntry(
                                                    type = com.personal.ptk.data.entities.RuleType.KEYWORD,
                                                    value = word
                                                )
                                            )
                                            suggestions = suggestions - word
                                        }
                                    }
                                }) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    onClick = onNavigateToRules,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, "Rules")
                        Spacer(Modifier.height(8.dp))
                        Text("Rules", style = MaterialTheme.typography.titleSmall)
                    }
                }
                Card(
                    onClick = onNavigateToVault,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Shield, "Vault")
                        Spacer(Modifier.height(8.dp))
                        Text("Vault", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int, modifier: Modifier = Modifier) {
    val formatted = remember(count) { NumberFormat.getNumberInstance().format(count) }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                formatted,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
