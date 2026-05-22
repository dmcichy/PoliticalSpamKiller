package com.personal.ptk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.personal.ptk.data.entities.RuleEntry
import com.personal.ptk.data.entities.RuleType
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Insert
    suspend fun insert(rule: RuleEntry)

    @Insert
    suspend fun insertAll(rules: List<RuleEntry>)

    @Query("SELECT * FROM rules WHERE type = :type AND enabled = 1 ORDER BY createdAt DESC")
    fun getByTypeFlow(type: RuleType): Flow<List<RuleEntry>>

    @Query("SELECT * FROM rules WHERE type = :type AND enabled = 1 ORDER BY createdAt DESC")
    suspend fun getByType(type: RuleType): List<RuleEntry>

    @Query("SELECT value FROM rules WHERE type = 'KEYWORD' AND enabled = 1")
    suspend fun getAllActiveKeywords(): List<String>

    @Query("SELECT COUNT(*) > 0 FROM rules WHERE type = 'ALLOWLIST_NUMBER' AND enabled = 1 AND value = :number")
    suspend fun isNumberAllowlisted(number: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM rules WHERE type = 'BLOCKLIST_NUMBER' AND enabled = 1 AND value = :number")
    suspend fun isNumberBlocklisted(number: String): Boolean

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM rules WHERE type = :type AND value = :value AND enabled = 1")
    suspend fun countByTypeAndValue(type: RuleType, value: String): Int
}
