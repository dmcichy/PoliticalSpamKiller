package com.personal.ptk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personal.ptk.data.entities.VaultEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: VaultEntry)

    @Query("SELECT * FROM vault_entries ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries ORDER BY timestamp DESC")
    suspend fun getAll(): List<VaultEntry>

    @Query("SELECT COUNT(*) FROM vault_entries WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM vault_entries")
    suspend fun countAll(): Int

    @Query("SELECT body FROM vault_entries WHERE timestamp >= :since")
    suspend fun getBodiesSince(since: Long): List<String>

    @Query("DELETE FROM vault_entries WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM vault_entries")
    suspend fun deleteAll()

    @Query("DELETE FROM vault_entries WHERE sender = :sender")
    suspend fun deleteAllBySender(sender: String): Int

    @Query("DELETE FROM vault_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM vault_entries WHERE reason = :reason AND matchedRule = :matchedRule")
    suspend fun deleteByReasonAndRule(reason: String, matchedRule: String): Int

    @Query("SELECT * FROM vault_entries WHERE smsId IS NOT NULL AND scrubbed = 0")
    suspend fun getPendingPurge(): List<VaultEntry>

    @Query("SELECT COUNT(*) FROM vault_entries WHERE smsId IS NOT NULL AND scrubbed = 0")
    fun countPendingPurgeFlow(): Flow<Int>

    @Query("UPDATE vault_entries SET smsId = :smsId WHERE id = :id")
    suspend fun setSmsId(id: Long, smsId: Long)

    @Query("UPDATE vault_entries SET scrubbed = 1 WHERE id = :id")
    suspend fun markScrubbed(id: Long)
}
