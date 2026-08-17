package com.familyguard.child.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that enqueues/cancels this app's background work — nothing else in the
 * app should call `WorkManager.getInstance(...)` directly, so scheduling policy stays in
 * one auditable spot.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Enqueues both periodic syncs. Call once after pairing completes, and again from BootCompletedReceiver. */
    fun scheduleAll(expediteRuleSync: Boolean = false) {
        scheduleRuleSyncPeriodic()
        scheduleUsageSyncPeriodic()
        if (expediteRuleSync) scheduleRuleSyncOneOff()
    }

    private fun scheduleRuleSyncPeriodic() {
        val request = PeriodicWorkRequestBuilder<RuleSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            RuleSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** One-off, expedited: used right after pairing (so rules are fresh before the first enforcement tick) and on boot. */
    fun scheduleRuleSyncOneOff() {
        val request = OneTimeWorkRequestBuilder<RuleSyncWorker>()
            .setConstraints(networkConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(
            RuleSyncWorker.WORK_NAME_ONE_OFF,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun scheduleUsageSyncPeriodic() {
        val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UsageSyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Used only if a device is revoked/unpaired — stops all background work until re-paired. */
    fun cancelAll() {
        workManager.cancelUniqueWork(RuleSyncWorker.WORK_NAME_PERIODIC)
        workManager.cancelUniqueWork(RuleSyncWorker.WORK_NAME_ONE_OFF)
        workManager.cancelUniqueWork(UsageSyncWorker.WORK_NAME_PERIODIC)
    }
}
