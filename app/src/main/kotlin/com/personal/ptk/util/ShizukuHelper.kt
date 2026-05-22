package com.personal.ptk.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.personal.ptk.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 42_001

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun isPermissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Log.w(TAG, "Could not request Shizuku permission", e)
        }
    }

    /**
     * True when Shizuku is enabled in preferences AND fully operational
     * (installed, running, permission granted).
     */
    fun isActive(context: Context): Boolean {
        val app = context.applicationContext as App
        return app.isShizukuPowerMode() && isRunning() && isPermissionGranted()
    }

    /**
     * Silently assigns the default SMS role to [pkg] using a shell command.
     * Returns true on success.
     */
    suspend fun silentSetDefaultSms(pkg: String): Boolean = withContext(Dispatchers.IO) {
        runShellCommand("cmd role add-role-holder android.app.role.SMS $pkg")
    }

    /**
     * Fast-path: attempt to delete a single SMS row directly from shell.
     * Returns true if the command succeeded (exit code 0).
     */
    suspend fun silentDeleteSms(smsId: Long): Boolean = withContext(Dispatchers.IO) {
        runShellCommand("content delete --uri content://sms/$smsId")
    }

    /**
     * Switches PTK to default, runs [block], then switches back to the
     * previous SMS app. Handles cleanup even if the block throws.
     */
    suspend fun withPtkAsDefault(context: Context, block: suspend () -> Unit) {
        val app = context.applicationContext as App
        val ptkPkg = app.packageName
        val previousPkg = SmsRoleHelper.previousSmsApp(context)

        val currentDefault = SmsRoleHelper.currentDefaultPackage(context)
        if (currentDefault != null && currentDefault != ptkPkg) {
            app.prefs.edit()
                .putString(App.PREF_PREVIOUS_SMS_APP, currentDefault)
                .apply()
        }

        try {
            silentSetDefaultSms(ptkPkg)
            delay(500)
            block()
        } finally {
            silentSetDefaultSms(previousPkg)
            delay(300)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun runShellCommand(command: String): Boolean {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null as Array<String>?,
                null as String?
            ) as Process
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val stderr = process.errorStream.bufferedReader().readText()
                Log.w(TAG, "Shell command failed ($exitCode): $command — $stderr")
            }
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Shell command exception: $command", e)
            false
        }
    }
}
