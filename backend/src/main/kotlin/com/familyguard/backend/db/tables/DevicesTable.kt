package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Mirrors V4__create_devices.sql exactly. `status` values: pending_approval | approved | revoked. */
object DevicesTable : UUIDTable("devices") {
    val childId = reference("child_id", ChildrenTable)
    val deviceName = text("device_name").nullable()
    val model = text("model").nullable()
    val osVersion = text("os_version").nullable()
    val appVersion = text("app_version").nullable()
    val status = text("status").default("pending_approval")
    val pendingClaimTicketHash = text("pending_claim_ticket_hash").nullable()
    val claimTicketExpiresAt = timestampWithTimeZone("claim_ticket_expires_at").nullable()
    val lastSeenAt = timestampWithTimeZone("last_seen_at").nullable()
    val lastSyncAt = timestampWithTimeZone("last_sync_at").nullable()
    val pairedAt = timestampWithTimeZone("paired_at").nullable()
    val approvedAt = timestampWithTimeZone("approved_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestamp)
}

/** String constants for DevicesTable.status, matching the DB CHECK constraint. */
object DeviceStatusValues {
    const val PENDING_APPROVAL = "pending_approval"
    const val APPROVED = "approved"
    const val REVOKED = "revoked"
}
