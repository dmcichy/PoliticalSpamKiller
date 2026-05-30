package com.personal.ptk.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.personal.ptk.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
     * Force-stops an app so it re-syncs its caches on next launch.
     * Used after deleting SMS rows to clear Google Messages' Bugle cache.
     */
    suspend fun forceStopPackage(pkg: String): Boolean = withContext(Dispatchers.IO) {
        runShellCommand("am force-stop $pkg")
    }

    /**
     * Fast-path: attempt to delete a single SMS row directly from shell.
     * Returns true if the command succeeded (exit code 0).
     */
    suspend fun silentDeleteSms(smsId: Long): Boolean = withContext(Dispatchers.IO) {
        runShellCommand("content delete --uri content://sms/$smsId")
    }

    /**
     * Re-inserts a previously deleted message back into the SMS inbox.
     * Used by the Kill Log "Restore" action. Passed as an argv array (not via
     * sh -c) so message bodies with quotes/newlines don't break shell parsing.
     * Returns true on success.
     */
    suspend fun silentInsertSms(sender: String, body: String, timestamp: Long): Boolean =
        withContext(Dispatchers.IO) {
            runShellArgv(
                arrayOf(
                    "content", "insert", "--uri", "content://sms/inbox",
                    "--bind", "address:s:$sender",
                    "--bind", "body:s:$body",
                    "--bind", "date:l:$timestamp",
                    "--bind", "read:i:1",
                    "--bind", "seen:i:1",
                    "--bind", "type:i:1"
                )
            )
        }

    /**
     * Switches PTK to default, runs [block], then switches back to the
     * previous SMS app. Handles cleanup even if the block throws.
     */
    suspend fun withPtkAsDefault(context: Context, block: suspend () -> Unit) {
        val app = context.applicationContext as App
        val ptkPkg = app.packageName

        val currentDefault = SmsRoleHelper.currentDefaultPackage(context)
        val previousPkg = if (currentDefault != null && currentDefault != ptkPkg) {
            app.prefs.edit()
                .putString(App.PREF_PREVIOUS_SMS_APP, currentDefault)
                .apply()
            currentDefault
        } else {
            SmsRoleHelper.previousSmsApp(context)
        }

        val switched = silentSetDefaultSms(ptkPkg)
        Log.d(TAG, "Switch TO PTK: success=$switched")
        if (switched) delay(500)

        try {
            block()
        } finally {
            // NonCancellable ensures this runs even if the coroutine is cancelled
            withContext(NonCancellable) {
                val restored = silentSetDefaultSms(previousPkg)
                Log.d(TAG, "Switch BACK to $previousPkg: success=$restored")
                if (!restored) {
                    // Retry once
                    delay(500)
                    val retry = silentSetDefaultSms(previousPkg)
                    Log.d(TAG, "Switch BACK retry: success=$retry")
                }
            }
        }
    }

    /**
     * Launches a self-contained shell script in Shizuku's process (UID 2000)
     * that orchestrates SMS deletion. This process is independent of PTK's
     * lifecycle — it survives the SIGKILL Android sends when the SMS role
     * changes.
     *
     * Script sequence:
     *  1. Switch default SMS to PTK  (triggers PTK process kill)
     *  2. sleep 2s — system settles, PTK restarts with new role
     *  3. am broadcast → PendingDeleteReceiver inside PTK runs
     *     ContentResolver.delete() as PTK's UID (Samsung honours it
     *     because PTK is now the default SMS app)
     *  4. sleep 2s for deletion to complete
     *  5. Switch default SMS back to previous app
     *  6. If the previous SMS app was in the foreground when the spam hit
     *     (i.e. the role switch yanked the user out of it), relaunch it so
     *     the user isn't dumped on the home screen. If they weren't looking
     *     at it, do nothing — the whole operation stays invisible.
     *
     * NOTE: We deliberately DO NOT force-stop Google Messages. Empirically,
     * force-stopping triggers GM's startup reconciliation, which re-populates
     * the just-deleted message from its internal Bugle cache (the ghost).
     * Letting GM restart naturally from the role change cleanly reflects the
     * emptied content://sms — no ghost thread.
     */
    fun fireAndForgetDelete(ptkPkg: String, restorePkg: String, smsId: Long) {
        try {
            val script =
                "FG=\$(dumpsys window | grep -i mCurrentFocus)" +
                " ; cmd role add-role-holder android.app.role.SMS $ptkPkg" +
                " && sleep 2" +
                " && am broadcast" +
                    " -a com.personal.ptk.DELETE_SMS" +
                    " --el smsId $smsId" +
                    " -n $ptkPkg/com.personal.ptk.sms.PendingDeleteReceiver" +
                " ; sleep 2" +
                " ; cmd role add-role-holder android.app.role.SMS $restorePkg" +
                " ; case \"\$FG\" in *$restorePkg*)" +
                    " monkey -p $restorePkg -c android.intent.category.LAUNCHER 1 ;;" +
                " esac"

            val cmd = arrayOf("sh", "-c", script)
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            method.invoke(
                null, cmd, null as Array<String>?, null as String?
            ) as Process
            Log.d(TAG, "Orchestration launched: smsId=$smsId restore=$restorePkg")
        } catch (e: Exception) {
            Log.e(TAG, "Orchestration launch failed", e)
        }
    }

    private fun runShellCommand(command: String): Boolean =
        runShellArgv(arrayOf("sh", "-c", command))

    @Suppress("UNCHECKED_CAST")
    private fun runShellArgv(cmd: Array<String>): Boolean {
        val label = cmd.joinToString(" ")
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
                cmd,
                null as Array<String>?,
                null as String?
            ) as Process

            // Read streams BEFORE waitFor to avoid deadlock on full buffers
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                Log.w(TAG, "Shell FAILED ($exitCode): $label — stderr=$stderr stdout=$stdout")
            } else {
                Log.d(TAG, "Shell OK: $label")
            }
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Shell EXCEPTION: $label", e)
            false
        }
    }
}
