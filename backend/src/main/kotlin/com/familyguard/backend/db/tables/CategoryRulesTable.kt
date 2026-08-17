package com.familyguard.backend.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/** Mirrors V13__create_category_rules.sql exactly. UNIQUE(child_id, category) is the upsert target. */
object CategoryRulesTable : UUIDTable("category_rules") {
    val childId = reference("child_id", ChildrenTable)
    val category = text("category")
    val dailyLimitMinutes = integer("daily_limit_minutes")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(childId, category)
    }
}
