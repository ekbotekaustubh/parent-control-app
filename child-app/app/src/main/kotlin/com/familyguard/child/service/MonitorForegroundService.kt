package com.familyguard.child.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.familyguard.child.R
import com.familyguard.child.data.repository.RuleRepository
import com.familyguard.child.data.repository.UsageRepository
import com.familyguard.child.domain.EnforcementDecider
import com.familyguard.child.domain.EnforcementResult
import com.familyguard.child.ui.restriction.RestrictionActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The core enforcement mechanism of this app.
 *
 * ## Honest limitation (see `docs/architecture.md` / `docs/roadmap.md`)
 * This is **detect-then-cover, not a preventive block**. It works by polling
 * [UsageStatsManager] roughly every 3 seconds for `MOVE_TO_FOREGROUND` events, and when a
 * restricted app is observed in the foreground, launching [RestrictionActivity] on top of
 * it. The restricted app's UI is briefly visible before the cover screen appears — a real,
 * if small, window. The deliberately-not-used stronger alternatives
 * (`AccessibilityService`, Device Admin, `VpnService`-based interception) are documented in
 * `docs/roadmap.md` as an explicit, considered future upgrade path, not an oversight: they
 * require a materially heavier consent flow and declared Play Store use-case per the
 * platform-policy constraint against requesting more than the minimum necessary sensitive
 * permission for this slice.
 *
 * Additionally, `PACKAGE_USAGE_STATS` is a special-access permission the user can revoke
 * from Settings at any time (see `service/UsageStatsPermissionChecker.kt`) — this service
 * checks for that on every loop iteration and stops enforcing (surfacing the gap via the
 * ongoing notification) rather than silently failing.
 *
 * Hard requirement: this service NEVER makes a network call. It only ever reads the local
 * Room cache (via [RuleRepository]/[UsageRepository]), which is what makes enforcement work
 * offline. All network I/O happens in `work/RuleSyncWorker.kt` and `work/UsageSyncWorker.kt`.
 */
@AndroidEntryPoint
class MonitorForegroundService : Service() {

    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var enforcementDecider: EnforcementDecider
    @Inject lateinit var permissionChecker: UsageStatsPermissionChecker

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)
    private var loopJob: Job? = null

    private var lastCheckpointMs: Long = 0L
    private var lastForegroundPackage: String? = null
    private var lastForegroundSinceMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        lastCheckpointMs = System.currentTimeMillis() - POLL_INTERVAL_MS
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (loopJob == null || loopJob?.isActive != true) {
            loopJob = serviceScope.launch { monitorLoop() }
        }
        // START_STICKY: if the system kills this process under memory pressure, restart it
        // with a null intent as soon as resources allow, rather than requiring the child to
        // manually relaunch the app for enforcement to resume.
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        while (true) {
            runCatching { tick() }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun tick() {
        if (!permissionChecker.isGranted()) {
            // Usage-access was revoked from Settings after the service started. We can't
            // read the foreground app anymore; surface this honestly instead of pretending
            // to still enforce. Enforcement resumes automatically once re-granted.
            updateNotification(getString(R.string.monitor_notification_permission_lost))
            return
        }

        val foregroundPackage = pollForegroundPackage() ?: return
        accrueTimeSinceLastTick(foregroundPackage)

        val cachedRules = ruleRepository.getCachedRules()
        val activeOverrides = ruleRepository.getCachedOverrides()
        val usageDate = usageRepository.todayDateString()
        val todayUsage = usageRepository.getTodayUsageMinutes(usageDate)

        when (val result = enforcementDecider.decide(cachedRules, todayUsage, foregroundPackage, activeOverrides)) {
            is EnforcementResult.Blocked -> launchRestriction(result)
            is EnforcementResult.LimitExceeded -> launchRestriction(result)
            EnforcementResult.Allowed -> Unit
        }

        updateNotification(getString(R.string.monitor_notification_active))
    }

    /** Reads MOVE_TO_FOREGROUND events since the last checkpoint; returns the most recent foreground package, if any. */
    private fun pollForegroundPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(lastCheckpointMs, now)
        var latestForegroundPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                latestForegroundPackage = event.packageName
            }
        }
        lastCheckpointMs = now
        return latestForegroundPackage ?: lastForegroundPackage
    }

    /** Accrues elapsed wall-clock time for the app that's been in the foreground, local-only. */
    private suspend fun accrueTimeSinceLastTick(currentForegroundPackage: String) {
        val now = System.currentTimeMillis()
        if (lastForegroundPackage != currentForegroundPackage) {
            lastForegroundPackage = currentForegroundPackage
            lastForegroundSinceMs = now
            return
        }
        val elapsedMinutes = ((now - lastForegroundSinceMs) / 60_000L).toInt()
        if (elapsedMinutes > 0) {
            val usageDate = usageRepository.todayDateString()
            usageRepository.accrueForegroundTime(currentForegroundPackage, usageDate, elapsedMinutes)
            // Advance the "since" pointer by exactly what we credited, not to `now`, so
            // sub-minute remainders aren't dropped on the floor between ticks.
            lastForegroundSinceMs += elapsedMinutes * 60_000L
        }
    }

    private fun launchRestriction(result: EnforcementResult) {
        val intent = RestrictionActivity.newIntent(this, result)
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String = getString(R.string.monitor_notification_active)): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "monitor_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 3_000L

        fun newIntent(context: Context): Intent = Intent(context, MonitorForegroundService::class.java)

        /** Starts (or is a no-op if already running) the monitor service. Safe to call repeatedly. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, newIntent(context))
        }
    }
}
