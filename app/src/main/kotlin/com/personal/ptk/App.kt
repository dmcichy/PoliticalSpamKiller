package com.personal.ptk

import android.app.Application
import android.content.SharedPreferences
import com.personal.ptk.billing.BillingManager
import com.personal.ptk.classify.Classifier
import com.personal.ptk.classify.DefaultKeywords
import com.personal.ptk.classify.MlClassifier
import com.personal.ptk.data.PtkDatabase
import com.personal.ptk.data.RoomRuleProvider
import com.personal.ptk.data.entities.RuleEntry
import com.personal.ptk.data.entities.RuleType
import com.personal.ptk.notif.KillRingBuffer
import com.personal.ptk.sms.ContactGuard
import com.personal.ptk.worker.WeeklySummaryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var database: PtkDatabase
        private set

    lateinit var classifier: Classifier
        private set

    val killRingBuffer = KillRingBuffer()

    lateinit var billingManager: BillingManager
        private set

    val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private val mlClassifier: MlClassifier by lazy { MlClassifier(this) }

    override fun onCreate() {
        super.onCreate()

        database = PtkDatabase.getInstance(this)

        classifier = Classifier(
            contactChecker = ContactGuard(contentResolver),
            ruleProvider = RoomRuleProvider(database.ruleDao()),
            mlClassifier = mlClassifier,
            isMlEnabled = { prefs.getBoolean(PREF_ML_ENABLED, false) }
        )

        billingManager = BillingManager(this)

        WeeklySummaryWorker.schedule(this)

        backfillDefaultKeywords()
    }

    private fun backfillDefaultKeywords() {
        CoroutineScope(Dispatchers.IO).launch {
            val ruleDao = database.ruleDao()

            // Remove keywords that proved too generic / caused false positives
            val retired = listOf(
                "pac", "urgent", "final notice", "last chance", "act now",
                "expiring", "deadline", "stop the", "defend our", "protect our",
                "save our", "supporter", "primary", "liberal", "conservative",
                "poll", "polls", "vote", "voter", "voted", "voting",
                "ballot", "election", "campaign", "candidate",
                "caucus", "precinct", "runoff", "electoral",
                "you in?", "patriot", "grassroots",
                "rush in", "rush a", "your contribution",
                "aoc", "mtg"
            )
            var removed = 0
            for (kw in retired) {
                removed += ruleDao.deleteKeyword(kw)
            }
            if (removed > 0) {
                android.util.Log.d("App", "Removed $removed retired keywords")
            }

            val existing = ruleDao.getAllActiveKeywords().map { it.lowercase().trim() }.toSet()
            val toAdd = DefaultKeywords.ALL.filter { it.lowercase().trim() !in existing }
            if (toAdd.isNotEmpty()) {
                ruleDao.insertAll(toAdd.map { RuleEntry(type = RuleType.KEYWORD, value = it) })
                android.util.Log.d("App", "Backfilled ${toAdd.size} new default keywords")
            }
        }
    }

    fun isKillSwitchEnabled(): Boolean = prefs.getBoolean(PREF_KILL_SWITCH, true)

    companion object {
        const val PREFS_NAME = "ptk_prefs"
        const val PREF_KILL_SWITCH = "kill_switch"
        const val PREF_ML_ENABLED = "ml_enabled"

        const val PREF_RETENTION_DAYS = "vault_retention_days"
        const val PREF_SETUP_COMPLETE = "setup_complete"
        const val PREF_PREVIOUS_SMS_APP = "previous_sms_app"
        const val PREF_SHIZUKU_ENABLED = "shizuku_enabled"
        const val PREF_AUTO_DELETE = "auto_delete_on_scan"
    }

    fun isShizukuPowerMode(): Boolean = prefs.getBoolean(PREF_SHIZUKU_ENABLED, false)
}
