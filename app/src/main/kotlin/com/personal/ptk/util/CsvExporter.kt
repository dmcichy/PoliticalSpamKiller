package com.personal.ptk.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.personal.ptk.data.VaultDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    suspend fun exportAndShare(context: Context, vaultDao: VaultDao) {
        withContext(Dispatchers.IO) {
            try {
                val entries = vaultDao.getAll()
                if (entries.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Vault is empty", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
                val filename = "ptk_vault_${dateFormat.format(Date())}.csv"
                val file = File(context.getExternalFilesDir(null), filename)

                file.bufferedWriter().use { writer ->
                    writer.write("id,sender,body,timestamp,reason,matchedRule,scrubbed")
                    writer.newLine()

                    for (entry in entries) {
                        writer.write(buildString {
                            append(entry.id).append(',')
                            append(escapeCsv(entry.sender)).append(',')
                            append(escapeCsv(entry.body)).append(',')
                            append(entry.timestamp).append(',')
                            append(escapeCsv(entry.reason)).append(',')
                            append(escapeCsv(entry.matchedRule)).append(',')
                            append(entry.scrubbed)
                        })
                        writer.newLine()
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Export Vault CSV")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Export failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
