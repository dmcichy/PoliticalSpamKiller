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

    /** Reset a previously scrubbed/confirmed entry so it reappears for review. */
    @Query("""
        UPDATE vault_entries 
        SET scrubbed = 0, confirmed = 0, smsId = :smsId, reason = :reason, matchedRule = :matchedRule
        WHERE sender = :sender AND body = :body AND timestamp = :timestamp 
          AND (scrubbed = 1 OR confirmed = 1)
    """)
    suspend fun resetExisting(sender: String, body: String, timestamp: Long, smsId: Long?, reason: String, matchedRule: String): Int

    @Query("SELECT * FROM vault_entries ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<VaultEntry>>

    /** Messages PTK has actually removed from the inbox (Power Mode kills + purges). */
    @Query("SELECT * FROM vault_entries WHERE scrubbed = 1 ORDER BY timestamp DESC")
    fun getKilledFlow(): Flow<List<VaultEntry>>

    @Query("SELECT COUNT(*) FROM vault_entries WHERE scrubbed = 1")
    fun countKilledFlow(): Flow<Int>

    /** Items needing review: unscrubbed and not yet confirmed as spam. */
    @Query("SELECT * FROM vault_entries WHERE scrubbed = 0 AND confirmed = 0 ORDER BY timestamp DESC")
    fun getPendingReviewFlow(): Flow<List<VaultEntry>>

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

    /** Remove all unscrubbed entries (both unconfirmed and confirmed) -- "Restore All". */
    @Query("DELETE FROM vault_entries WHERE scrubbed = 0")
    suspend fun deleteAllPendingReview(): Int

    @Query("DELETE FROM vault_entries WHERE sender = :sender")
    suspend fun deleteAllBySender(sender: String): Int

    @Query("DELETE FROM vault_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM vault_entries WHERE reason = :reason AND matchedRule = :matchedRule")
    suspend fun deleteByReasonAndRule(reason: String, matchedRule: String): Int

    @Query("UPDATE vault_entries SET confirmed = 1 WHERE reason = :reason AND matchedRule = :matchedRule AND scrubbed = 0")
    suspend fun confirmByReasonAndRule(reason: String, matchedRule: String): Int

    @Query("UPDATE vault_entries SET confirmed = 1 WHERE id IN (:ids)")
    suspend fun confirmByIds(ids: List<Long>): Int

    @Query("UPDATE vault_entries SET confirmed = 1 WHERE scrubbed = 0 AND confirmed = 0 AND sender = :sender")
    suspend fun confirmBySender(sender: String): Int

    @Query("DELETE FROM vault_entries WHERE scrubbed = 0 AND sender = :sender")
    suspend fun deleteAllBySenderUnscrubbed(sender: String): Int

    @Query("SELECT * FROM vault_entries WHERE scrubbed = 0 AND confirmed = 1")
    suspend fun getPendingPurge(): List<VaultEntry>

    @Query("SELECT COUNT(*) FROM vault_entries WHERE scrubbed = 0 AND confirmed = 1")
    fun countPendingPurgeFlow(): Flow<Int>

    @Query("UPDATE vault_entries SET smsId = :smsId WHERE id = :id")
    suspend fun setSmsId(id: Long, smsId: Long)

    @Query("UPDATE vault_entries SET scrubbed = 1 WHERE id = :id")
    suspend fun markScrubbed(id: Long)

    @Query("UPDATE vault_entries SET scrubbed = 1 WHERE smsId = :smsId")
    suspend fun markScrubbedBySmsId(smsId: Long)
}
