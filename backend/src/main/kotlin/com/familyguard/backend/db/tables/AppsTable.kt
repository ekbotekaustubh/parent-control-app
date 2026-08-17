package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Mirrors V6__create_apps.sql exactly. Global catalog, not per-child. */
object AppsTable : UUIDTable("apps") {
    val packageName = text("package_name").uniqueIndex()
    val displayName = text("display_name").nullable()
    val category = text("category").nullable()
    val iconUrl = text("icon_url").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestamp)
}
