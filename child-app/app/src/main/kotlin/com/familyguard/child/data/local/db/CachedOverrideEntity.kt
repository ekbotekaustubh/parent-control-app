package com.familyguard.child.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of one app's active access-request override, synced from `GET
 * /device/config`'s `overrides` field (see `docs/api-spec.md`) by `work/RuleSyncWorker.kt`
 * and read exclusively by `service/MonitorForegroundService.kt`, same split as
 * [CachedRuleEntity]. `expiresAtMillis` is stored as-is rather than deleting the row at
 * expiry time — [CachedOverrideDao.getActive] filters it at read time, and the next sync
 * (at most 15 minutes later, `work/WorkScheduler.kt`) drops any truly stale row anyway.
 */
@Entity(tableName = "cached_overrides")
data class CachedOverrideEntity(
    @PrimaryKey val packageName: String,
    val extraMinutes: Int,
    val expiresAtMillis: Long,
)
