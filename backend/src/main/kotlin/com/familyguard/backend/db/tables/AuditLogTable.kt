package com.familyguard.backend.db.tables

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb

/** Mirrors V10__create_audit_log.sql exactly. `actor_type`: parent|device|system. */
object AuditLogTable : UUIDTable("audit_log") {
    val actorType = text("actor_type").nullable()
    val actorId = uuid("actor_id").nullable()
    val action = text("action")
    val targetType = text("target_type").nullable()
    val targetId = uuid("target_id").nullable()
    val metadata = jsonb<JsonElement>("metadata", Json).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
}

object AuditActorType {
    const val PARENT = "parent"
    const val DEVICE = "device"
    const val SYSTEM = "system"
}
