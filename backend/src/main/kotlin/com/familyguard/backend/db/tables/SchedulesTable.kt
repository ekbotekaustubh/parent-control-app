package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Mirrors V14__create_schedules.sql exactly. `mode`: block|allow_only (see that migration's comment). */
object SchedulesTable : UUIDTable("schedules") {
    val childId = reference("child_id", ChildrenTable)
    val name = text("name")
    val daysOfWeek = text("days_of_week")
    val startTime = time("start_time")
    val endTime = time("end_time")
    val mode = text("mode").default("block")
    val isActive = bool("is_active").default(true)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestamp)
}

object ScheduleModeValues {
    const val BLOCK = "block"
    const val ALLOW_ONLY = "allow_only"
}
