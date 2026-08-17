package com.familyguard.parent.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of the parent's child list, purely for instant paint on ChildListScreen
 * before the network response lands and as a pull-to-refresh fallback when offline. The
 * backend is always the source of truth (docs/architecture.md) — this table is rebuilt
 * wholesale from `GET /children` on every successful fetch, never written to independently.
 */
@Entity(tableName = "child_cache")
data class ChildCacheEntity(
    @PrimaryKey val id: String,
    val parentId: String,
    val name: String,
    val birthYear: Int?,
    val configVersion: Long,
    val status: String,
    val createdAt: String,
)
