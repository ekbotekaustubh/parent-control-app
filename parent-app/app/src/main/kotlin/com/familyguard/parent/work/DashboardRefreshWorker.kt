package com.familyguard.parent.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.familyguard.parent.data.repository.ChildRepository
import com.familyguard.parent.data.repository.DashboardRepository
import com.familyguard.parent.domain.OnlineStatus
import com.familyguard.parent.domain.OnlineStatusCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Periodic (15 min) background warmer for the dashboard cache, and the source of the
 * "device went offline" / "sync issue" local notifications.
 *
 * This is explicitly the **interim** mechanism, not the final design: docs/roadmap.md's
 * Notifications section describes upgrading these event types to server-pushed FCM once a
 * live send path exists (the dependency is already planned for, just not wired up). Polling
 * every 15 minutes means an offline notification can lag reality by up to that long — an
 * acceptable tradeoff for this slice, not a permanent architecture choice.
 *
 * Registered as a Hilt worker (`@HiltWorker` + `HiltWorkerFactory` wired in
 * `ParentApp.workManagerConfiguration`) so it can constructor-inject repositories exactly
 * like a ViewModel would, rather than reaching into a service locator.
 */
@HiltWorker
class DashboardRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val childRepository: ChildRepository,
    private val dashboardRepository: DashboardRepository,
    private val notifier: DeviceStatusNotifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        notifier.ensureChannel()

        val children = childRepository.refreshChildren().getOrElse {
            // Network unreachable / server error: leave the cache as-is and let
            // WorkManager's backoff policy retry on the next cycle rather than treating a
            // single failed warm-up as fatal.
            return Result.retry()
        }

        val now = Clock.System.now()
        var anySyncIssue = false

        for (child in children) {
            val previousStatus = dashboardRepository.getCachedDashboard(child.id)
                ?.let { onlineStatusOf(it.lastSeenAt, now) }

            val freshDashboard = dashboardRepository.refreshDashboard(child.id).getOrNull()
            if (freshDashboard == null) {
                anySyncIssue = true
                continue
            }

            val freshStatus = onlineStatusOf(freshDashboard.device.lastSeenAt, now)
            if (previousStatus == OnlineStatus.ONLINE && freshStatus == OnlineStatus.OFFLINE) {
                notifier.notifyDeviceOffline(child.name)
            }
        }

        if (anySyncIssue && children.size == 1) {
            // Only worth a dedicated "sync issue" nudge when it's unambiguous which child it
            // affects; with multiple children a partial-failure notification would be more
            // confusing than helpful for this slice, so it's silently retried next cycle.
            notifier.notifySyncIssue(children.first().name)
        }

        return Result.success()
    }

    private fun onlineStatusOf(lastSeenAt: String?, now: Instant): OnlineStatus =
        OnlineStatusCalculator.compute(lastSeenAt?.let { runCatching { Instant.parse(it) }.getOrNull() }, now)

    companion object {
        const val UNIQUE_WORK_NAME = "dashboard_refresh_worker"
        const val REPEAT_INTERVAL_MINUTES = 15L
    }
}
