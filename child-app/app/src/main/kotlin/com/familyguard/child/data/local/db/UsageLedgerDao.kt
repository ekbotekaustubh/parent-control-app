package com.familyguard.child.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageLedgerDao {

    @Query("SELECT * FROM usage_ledger WHERE usageDate = :usageDate")
    fun observeForDate(usageDate: String): Flow<List<UsageLedgerEntity>>

    @Query("SELECT * FROM usage_ledger WHERE usageDate = :usageDate")
    suspend fun getForDate(usageDate: String): List<UsageLedgerEntity>

    @Query("SELECT * FROM usage_ledger WHERE packageName = :packageName AND usageDate = :usageDate LIMIT 1")
    suspend fun get(packageName: String, usageDate: String): UsageLedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UsageLedgerEntity)

    @Query("SELECT * FROM usage_ledger WHERE durationMinutes > lastSyncedDurationMinutes")
    suspend fun getUnsyncedRows(): List<UsageLedgerEntity>

    /**
     * Marks [syncedValue] as confirmed-accepted by the backend for (packageName, usageDate),
     * but never *lowers* [UsageLedgerEntity.lastSyncedDurationMinutes] — new foreground time
     * may have accrued locally between when a snapshot was read for sending and when the
     * server responded, so this only ever advances the watermark, never regresses it.
     */
    @Query(
        "UPDATE usage_ledger SET lastSyncedDurationMinutes = :syncedValue " +
            "WHERE packageName = :packageName AND usageDate = :usageDate " +
            "AND lastSyncedDurationMinutes < :syncedValue",
    )
    suspend fun markSyncedUpTo(packageName: String, usageDate: String, syncedValue: Int)

    /**
     * Accrues [deltaMinutes] of newly observed foreground time onto the existing cumulative
     * total for (packageName, usageDate), creating the row if it doesn't exist yet. This is
     * the only write path the foreground service uses — always additive to the local
     * cumulative total, independent of connectivity.
     */
    @Transaction
    suspend fun accrue(packageName: String, usageDate: String, deltaMinutes: Int, now: Long) {
        val existing = get(packageName, usageDate)
        if (existing == null) {
            upsert(
                UsageLedgerEntity(
                    packageName = packageName,
                    usageDate = usageDate,
                    durationMinutes = deltaMinutes,
                    lastSyncedDurationMinutes = 0,
                    updatedAt = now,
                ),
            )
        } else {
            upsert(
                existing.copy(
                    durationMinutes = existing.durationMinutes + deltaMinutes,
                    updatedAt = now,
                ),
            )
        }
    }
}
