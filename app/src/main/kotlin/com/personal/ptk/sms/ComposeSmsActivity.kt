package com.personal.ptk.sms

import android.app.Activity
import android.os.Bundle

/**
 * Required stub to qualify as a default SMS app.
 * Handles SENDTO intents for sms:/smsto: URIs.
 * We don't compose messages so this immediately finishes.
 */
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
