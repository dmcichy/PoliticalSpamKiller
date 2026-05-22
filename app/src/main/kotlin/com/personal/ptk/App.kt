package com.personal.ptk

import android.app.Application
import android.content.SharedPreferences
import com.personal.ptk.classify.Classifier
import com.personal.ptk.classify.MlClassifier
import com.personal.ptk.data.PtkDatabase
import com.personal.ptk.data.RoomRuleProvider
import com.personal.ptk.notif.KillRingBuffer
import com.personal.ptk.sms.ContactGuard
import com.personal.ptk.worker.WeeklySummaryWorker

class App : Application() {

    lateinit var database: PtkDatabase
        private set

    lateinit var classifier: Classifier
        private set

    val killRingBuffer = KillRingBuffer()

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
            isMlEnabled = { prefs.getBoolean(PREF_ML_ENABLED, false) },
            isNuclearMode = { prefs.getBoolean(PREF_NUCLEAR_MODE, false) }
        )

        WeeklySummaryWorker.schedule(this)
    }

    fun isKillSwitchEnabled(): Boolean = prefs.getBoolean(PREF_KILL_SWITCH, true)

    companion object {
        const val PREFS_NAME = "ptk_prefs"
        const val PREF_KILL_SWITCH = "kill_switch"
        const val PREF_ML_ENABLED = "ml_enabled"
        const val PREF_NUCLEAR_MODE = "nuclear_shortcode_mode"
        const val PREF_RETENTION_DAYS = "vault_retention_days"
        const val PREF_SETUP_COMPLETE = "setup_complete"
        const val PREF_PREVIOUS_SMS_APP = "previous_sms_app"
    }
}
