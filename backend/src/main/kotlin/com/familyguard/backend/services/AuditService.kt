package com.familyguard.backend.services

import com.familyguard.backend.db.tables.AuditLogTable
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.insert
import java.util.UUID

/**
 * Small helper for writing audit_log rows. Callers already run inside an Exposed
 * `transaction { }` block (services never open a new one here), so the write is part of
 * whatever larger operation triggered it.
 */
object AuditService {
    fun log(
        actorType: String?,
        actorId: UUID?,
        action: String,
        targetType: String? = null,
        targetId: UUID? = null,
        metadata: JsonElement? = null,
    ) {
        AuditLogTable.insert {
            it[AuditLogTable.actorType] = actorType
            it[AuditLogTable.actorId] = actorId
            it[AuditLogTable.action] = action
            it[AuditLogTable.targetType] = targetType
            it[AuditLogTable.targetId] = targetId
            it[AuditLogTable.metadata] = metadata
        }
    }
}
