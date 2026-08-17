package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Mirrors V3__create_children.sql exactly. */
object ChildrenTable : UUIDTable("children") {
    // reference(...) (not uuid().references()) because ParentsTable.id is Column<EntityID<UUID>>,
    // not Column<UUID> - this is the Exposed idiom for FKs into a UUIDTable's id column.
    val parentId = reference("parent_id", ParentsTable)
    val name = text("name")
    val birthYear = integer("birth_year").nullable()
    val configVersion = long("config_version").default(1L)
    val status = text("status").default("active")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestamp)
}
