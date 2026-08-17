package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Mirrors V9__create_refresh_tokens.sql (parent_refresh_tokens half) exactly. */
object ParentRefreshTokensTable : UUIDTable("parent_refresh_tokens") {
    val parentId = reference("parent_id", ParentsTable)
    val tokenHash = text("token_hash")
    val issuedAt = timestampWithTimeZone("issued_at").defaultExpression(CurrentTimestamp)
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val replacedById = reference("replaced_by_id", ParentRefreshTokensTable).nullable()
    val userAgent = text("user_agent").nullable()
    val ipAddress = text("ip_address").nullable()
}
