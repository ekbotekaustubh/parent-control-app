package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Mirrors V2__create_parents.sql exactly. */
object ParentsTable : UUIDTable("parents") {
    val email = text("email").uniqueIndex()
    val passwordHash = text("password_hash")
    val displayName = text("display_name").nullable()
    val timezone = text("timezone").default("UTC")
    val status = text("status").default("active")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestamp)
}
