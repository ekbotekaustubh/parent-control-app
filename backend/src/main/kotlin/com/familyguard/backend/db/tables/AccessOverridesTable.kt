package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Mirrors V12__create_access_overrides.sql exactly. One row per approved
 * [AccessRequestsTable] row (`UNIQUE (access_request_id)`) — created by
 * `AccessRequestService.resolve()` on approval, consumed by `GET /device/config` and, on
 * the child app side, `EnforcementDecider`. See that class's KDoc for how `extraMinutes`
 * combines with the standing `app_rules` row.
 */
object AccessOverridesTable : UUIDTable("access_overrides") {
    val childId = reference("child_id", ChildrenTable)
    val appId = reference("app_id", AppsTable)
    val accessRequestId = reference("access_request_id", AccessRequestsTable)
    val extraMinutes = integer("extra_minutes")
    val expiresAt = timestampWithTimeZone("expires_at")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
}
