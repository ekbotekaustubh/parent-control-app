package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Mirrors V8__create_usage_records.sql exactly. UNIQUE(device_id, app_id, usage_date) is the
 * upsert target for the GREATEST-upsert in UsageService (written as raw SQL, not via this
 * Exposed DSL — this table object is used for reads/joins in DashboardService).
 */
object UsageRecordsTable : UUIDTable("usage_records") {
    val deviceId = reference("device_id", DevicesTable)
    val childId = reference("child_id", ChildrenTable)
    val appId = reference("app_id", AppsTable)
    val usageDate = date("usage_date")
    val durationMinutes = integer("duration_minutes").default(0)
    val lastUpdatedAt = timestampWithTimeZone("last_updated_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(deviceId, appId, usageDate)
    }
}
