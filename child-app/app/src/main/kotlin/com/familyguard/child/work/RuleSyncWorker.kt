package com.familyguard.child.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyguard.child.data.repository.RuleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic (15 min) + one-off expedited (from `BootCompletedReceiver` and right after
 * pairing completes) pull of `GET /device/config` into the local Room cache. This is the
 * ONLY path that updates `CachedRuleEntity` — `MonitorForegroundService` never calls the
 * network itself; it only ever reads what this worker most recently wrote.
 */
@HiltWorker
class RuleSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ruleRepository: RuleRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = ruleRepository.syncFromBackend()
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "rule_sync_periodic"
        const val WORK_NAME_ONE_OFF = "rule_sync_one_off"
    }
}
