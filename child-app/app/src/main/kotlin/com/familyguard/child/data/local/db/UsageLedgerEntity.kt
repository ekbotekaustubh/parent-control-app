package com.familyguard.child.data.local.db

import androidx.room.Entity

/**
 * Local, continuously-accruing per-app-per-day usage ledger, written by
 * `service/MonitorForegroundService.kt` (via `UsageRepository`) regardless of connectivity.
 * `durationMinutes` is this device's own running cumulative total for that day; it is never
 * reset except by the day rolling over (new `usageDate` composite key).
 *
 * `lastSyncedDurationMinutes` tracks the value already confirmed accepted by the backend,
 * so `UsageRepository` can tell whether there's anything new to enqueue without re-deriving
 * it from the sync queue.
 */
@Entity(tableName = "usage_ledger", primaryKeys = ["packageName", "usageDate"])
data class UsageLedgerEntity(
    val packageName: String,
    /** yyyy-MM-dd, device's local day — matches `UsageEvent.usageDate` in shared/. */
    val usageDate: String,
    val durationMinutes: Int,
    val lastSyncedDurationMinutes: Int,
    val updatedAt: Long,
)
