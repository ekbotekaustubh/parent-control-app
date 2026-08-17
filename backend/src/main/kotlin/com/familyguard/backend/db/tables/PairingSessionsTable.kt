package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Mirrors V5__create_pairing_sessions.sql exactly. `status`: pending|claimed|expired|revoked. */
object PairingSessionsTable : UUIDTable("pairing_sessions") {
    val childId = reference("child_id", ChildrenTable)
    val codeHash = text("code_hash")
    val status = text("status").default("pending")
    val expiresAt = timestampWithTimeZone("expires_at")
    val claimedByDeviceId = reference("claimed_by_device_id", DevicesTable).nullable()
    val claimedAt = timestampWithTimeZone("claimed_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
}

object PairingSessionStatusValues {
    const val PENDING = "pending"
    const val CLAIMED = "claimed"
    const val EXPIRED = "expired"
    const val REVOKED = "revoked"
}
