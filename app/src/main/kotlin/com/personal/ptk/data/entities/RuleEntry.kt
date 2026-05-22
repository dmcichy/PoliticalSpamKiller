package com.personal.ptk.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RuleType,
    val value: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
