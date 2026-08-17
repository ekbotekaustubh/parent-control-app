package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Mirrors V7__create_app_rules.sql plus V15__add_schedule_id_to_app_rules.sql. `rule_type`:
 * block|allow. UNIQUE(child_id, app_id). `scheduleId`: optional per-app/per-day window (see
 * that migration's comment and `docs/roadmap.md`'s "Schedules" section) - null means the
 * rule always applies, matching every row created before V15.
 */
object AppRulesTable : UUIDTable("app_rules") {
    val childId = reference("child_id", ChildrenTable)
    val appId = reference("app_id", AppsTable)
    val ruleType = text("rule_type").default("block")
    val dailyLimitMinutes = integer("daily_limit_minutes").nullable()
    val isActive = bool("is_active").default(true)
    val scheduleId = reference("schedule_id", SchedulesTable).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(childId, appId)
    }
}

object RuleTypeValues {
    const val BLOCK = "block"
    const val ALLOW = "allow"
}
