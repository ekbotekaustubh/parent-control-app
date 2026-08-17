package com.familyguard.parent.domain

import kotlinx.datetime.Instant

/**
 * Pure, Android-free "is this device online" decision — mirrors child-app's
 * `EnforcementDecider` pattern (docs/testing-plan.md): the business rule lives in a plain
 * Kotlin/JVM-testable class with zero framework dependency, independent of how/when it's
 * called from a ViewModel or a WorkManager worker.
 *
 * Why compute this client-side at all, when `GET /children/{id}/dashboard` already returns
 * a server-computed `device.online` boolean (docs/api-spec.md)? Two reasons:
 *  1. The parent app paints an instant "last known" snapshot from the local Room cache
 *     (data/local/db/DashboardCacheEntity.kt) before a fresh network response arrives, and
 *     that cached `online` boolean grows staler by the second while it's on screen — a
 *     local recompute from the cached `lastSeenAt` timestamp against "now" stays accurate
 *     even though the underlying fetch is old.
 *  2. DashboardRefreshWorker (work/DashboardRefreshWorker.kt) needs this same decision to
 *     detect an online-to-offline transition worth a local notification, independent of
 *     whatever the server's snapshot said at fetch time.
 */
enum class OnlineStatus {
    ONLINE,
    OFFLINE,
    /** No `lastSeenAt` at all yet — e.g. a device that has been approved but never synced. */
    UNKNOWN,
}

object OnlineStatusCalculator {

    /** Matches the ~10 minute online/offline threshold implied by dashboard heartbeat cadence. */
    val ONLINE_THRESHOLD_MINUTES = 10L

    fun compute(lastSeenAt: Instant?, now: Instant): OnlineStatus {
        if (lastSeenAt == null) return OnlineStatus.UNKNOWN
        if (lastSeenAt > now) return OnlineStatus.ONLINE // clock skew guard — treat as fresh
        val minutesSinceSeen = (now - lastSeenAt).inWholeMinutes
        return if (minutesSinceSeen <= ONLINE_THRESHOLD_MINUTES) OnlineStatus.ONLINE else OnlineStatus.OFFLINE
    }
}
