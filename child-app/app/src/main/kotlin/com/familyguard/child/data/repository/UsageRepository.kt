package com.familyguard.child.data.repository

import com.familyguard.child.data.local.SyncMetadataStore
import com.familyguard.child.data.local.db.SyncQueueDao
import com.familyguard.child.data.local.db.SyncQueueEntity
import com.familyguard.child.data.local.db.UsageLedgerDao
import com.familyguard.child.data.remote.ChildApiService
import com.familyguard.shared.dto.UsageEvent
import com.familyguard.shared.dto.UsageSyncRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the local usage ledger (continuous, offline-safe accrual, written by
 * `MonitorForegroundService`) and the outbox-style sync queue that
 * `work/UsageSyncWorker.kt` drains to `POST /device/usage-sync`.
 *
 * IMPORTANT: [accrueForegroundTime] must never touch the network — it is called from the
 * foreground service's ~3s polling loop and must complete fast and offline. Network I/O
 * only happens in [drainSyncQueue], called from the periodic worker.
 */
@Singleton
class UsageRepository @Inject constructor(
    private val api: ChildApiService,
    private val ledgerDao: UsageLedgerDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncMetadataStore: SyncMetadataStore,
) {

    fun todayDateString(): String = LocalDate.now().toString() // yyyy-MM-dd

    /** Accrues [deltaMinutes] of newly observed foreground time. Local-only, no network. */
    suspend fun accrueForegroundTime(packageName: String, usageDate: String, deltaMinutes: Int) {
        if (deltaMinutes <= 0) return
        ledgerDao.accrue(packageName, usageDate, deltaMinutes, System.currentTimeMillis())
    }

    fun observeTodayUsageMinutes(usageDate: String): Flow<Map<String, Int>> =
        ledgerDao.observeForDate(usageDate).map { rows -> rows.associate { it.packageName to it.durationMinutes } }

    suspend fun getTodayUsageMinutes(usageDate: String): Map<String, Int> =
        ledgerDao.getForDate(usageDate).associate { it.packageName to it.durationMinutes }

    /**
     * Snapshots every ledger row with unsynced deltas into the outbox (`sync_queue`).
     * Cumulative snapshots, not per-call deltas — matches the backend's `GREATEST`-upsert,
     * so re-enqueuing/resending is always safe (hard requirement #4).
     */
    suspend fun enqueuePendingSnapshots() {
        val unsynced = ledgerDao.getUnsyncedRows()
        val now = System.currentTimeMillis()
        unsynced.forEach { row ->
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    packageName = row.packageName,
                    usageDate = row.usageDate,
                    durationMinutes = row.durationMinutes,
                    createdAt = now,
                ),
            )
        }
    }

    /**
     * Drains the outbox: collapses queued rows per (packageName, usageDate) to their max
     * cumulative value (multiple snapshots may have piled up while offline — sending the
     * highest is sufficient since older, lower values are strictly dominated), POSTs them,
     * and on success clears those rows and advances the ledger's synced watermark.
     *
     * Returns true if the sync attempt succeeded (including the trivial case of an empty
     * queue), false on network/API failure — the caller (`UsageSyncWorker`) uses this to
     * decide whether to report retry to WorkManager.
     */
    suspend fun drainSyncQueue(): Boolean {
        val queued = syncQueueDao.getAll()
        if (queued.isEmpty()) return true

        val collapsed = queued
            .groupBy { it.packageName to it.usageDate }
            .mapValues { (_, rows) -> rows.maxOf { it.durationMinutes } }

        val events = collapsed.map { (key, minutes) ->
            UsageEvent(packageName = key.first, usageDate = key.second, durationMinutes = minutes)
        }

        return try {
            api.syncUsage(UsageSyncRequest(events))
            collapsed.forEach { (key, minutes) ->
                val (packageName, usageDate) = key
                syncQueueDao.deleteForKey(packageName, usageDate)
                ledgerDao.markSyncedUpTo(packageName, usageDate, minutes)
            }
            syncMetadataStore.lastUsageSyncAtMillis = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            // Leave the queue rows in place for the next scheduled attempt; WorkManager's
            // exponential backoff (configured in work/UsageSyncWorker.kt) governs retry timing.
            false
        }
    }
}
