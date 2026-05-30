package com.personal.ptk.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_entries",
    indices = [Index(value = ["sender", "body", "timestamp"], unique = true)]
)
data class VaultEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val reason: String,
    val matchedRule: String,
    val scrubbed: Boolean,
    /** Row ID in content://sms when known. Null until we can locate it. */
    val smsId: Long? = null,
    /** True when user has approved this as spam (hides from vault review, still eligible for purge). */
    val confirmed: Boolean = false
)
