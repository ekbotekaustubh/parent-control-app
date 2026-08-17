package com.familyguard.child.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of one app's rule, synced from `GET /device/config`
 * (see `docs/api-spec.md`) by `work/RuleSyncWorker.kt` and read exclusively by
 * `service/MonitorForegroundService.kt` for enforcement — enforcement never blocks on
 * network, only on this table.
 */
@Entity(tableName = "cached_rules")
data class CachedRuleEntity(
    @PrimaryKey val packageName: String,
    /** "block" or "allow" — stored as the raw string form of shared's RuleType enum. */
    val ruleType: String,
    val dailyLimitMinutes: Int?,
    /** The server's `configVersion` this row was last synced under; lets sync short-circuit when unchanged. */
    val configVersion: Long,
    val updatedAt: Long,
    /** docs/roadmap.md's "Category-level rules" — see EnforcementRule.category's KDoc. */
    val category: String? = null,
    /** docs/roadmap.md's "Schedules" (per-app variant) — see EnforcementRule.scheduleId's KDoc. */
    val scheduleId: String? = null,
)
