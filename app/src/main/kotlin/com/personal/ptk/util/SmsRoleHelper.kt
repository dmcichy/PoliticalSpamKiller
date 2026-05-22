package com.personal.ptk.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import com.personal.ptk.App

object SmsRoleHelper {

    private const val TAG = "SmsRoleHelper"
    private const val GOOGLE_MESSAGES = "com.google.android.apps.messaging"

    fun isDefaultSmsApp(context: Context): Boolean {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    }

    fun currentDefaultPackage(context: Context): String? {
        val pkg = Telephony.Sms.getDefaultSmsPackage(context)
        if (pkg != null) return pkg
        if (isDefaultSmsApp(context)) return context.packageName
        return null
    }

    fun appLabel(context: Context, pkg: String): String {
        val known = knownLabel(pkg)
        if (known != null) return known
        return try {
            val ai = context.packageManager.getApplicationInfo(pkg, PackageManager.MATCH_ALL)
            context.packageManager.getApplicationLabel(ai).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Could not resolve label for $pkg", e)
            pkg
        }
    }

    private fun knownLabel(pkg: String): String? = when (pkg) {
        GOOGLE_MESSAGES -> "Google Messages"
        "com.samsung.android.messaging" -> "Samsung Messages"
        else -> null
    }

    fun makePtkDefaultIntent(context: Context): Intent {
        val app = context.applicationContext as App
        val current = currentDefaultPackage(context)
        if (current != null && current != context.packageName) {
            app.prefs.edit()
                .putString(App.PREF_PREVIOUS_SMS_APP, current)
                .apply()
        }
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    }

    /**
     * Opens the system default-apps settings so the user can pick their
     * previous SMS app. Samsung Android 15 silently eats the deprecated
     * ACTION_CHANGE_DEFAULT, so we go straight to the settings page.
     */
    fun launchSwitchBack(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun previousSmsApp(context: Context): String {
        val app = context.applicationContext as App
        return app.prefs.getString(App.PREF_PREVIOUS_SMS_APP, null) ?: GOOGLE_MESSAGES
    }

    fun previousAppLabel(context: Context): String {
        return appLabel(context, previousSmsApp(context))
    }
}
