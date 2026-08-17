package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Mirrors V11__create_access_requests.sql exactly. `status`: pending|approved|denied. */
object AccessRequestsTable : UUIDTable("access_requests") {
    val childId = reference("child_id", ChildrenTable)
    val appId = reference("app_id", AppsTable)
    val requestedMinutes = integer("requested_minutes")
    val status = text("status").default("pending")
    val resolvedMinutes = integer("resolved_minutes").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
    val resolvedAt = timestampWithTimeZone("resolved_at").nullable()
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestamp)
}

object AccessRequestStatusValues {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val DENIED = "denied"
}
