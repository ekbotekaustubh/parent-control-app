package com.familyguard.child.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyguard.child.data.repository.UsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic (15 min), network-constrained drain of the local usage-ledger outbox to
 * `POST /device/usage-sync`. Every event sent is a cumulative per-(packageName, usageDate)
 * snapshot, not a delta (hard requirement #4) — matches the backend's
 * `GREATEST(existing, incoming)` upsert (see `docs/database-schema.md`), so a queue that
 * built up while offline can be replayed on reconnect with no client-side dedup logic.
 *
 * Exponential backoff (configured on the `WorkRequest` in `WorkScheduler.kt`) governs
 * retry timing when [Result.retry] is returned.
 */
@HiltWorker
class UsageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val usageRepository: UsageRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        usageRepository.enqueuePendingSnapshots()
        val succeeded = usageRepository.drainSyncQueue()
        return if (succeeded) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME_PERIODIC = "usage_sync_periodic"
    }
}
