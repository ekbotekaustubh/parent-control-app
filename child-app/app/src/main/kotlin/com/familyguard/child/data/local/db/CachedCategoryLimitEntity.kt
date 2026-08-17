package com.familyguard.child.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of one category's daily limit, synced from `GET /device/config`'s
 * `categoryRules` field. See `docs/database-schema.md`'s `category_rules` entry for the
 * scope limit (only consulted for apps that already have a standing unlimited ALLOW rule).
 */
@Entity(tableName = "cached_category_limits")
data class CachedCategoryLimitEntity(
    @PrimaryKey val category: String,
    val dailyLimitMinutes: Int,
)
