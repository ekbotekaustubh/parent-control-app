package com.familyguard.parent.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of the last-known `GET /children/{childId}/dashboard` response (one row per
 * child), for instant paint + pull-to-refresh on DashboardScreen and for
 * work/DashboardRefreshWorker.kt to warm in the background. `rulesJson`/`usageTodayJson`
 * hold the nested list DTOs serialized as JSON (Room has no native list-of-object column
 * type); see DashboardCacheMappers.kt for the (de)serialization.
 */
@Entity(tableName = "dashboard_cache")
data class DashboardCacheEntity(
    @PrimaryKey val childId: String,
    val deviceStatus: String,
    val deviceOnline: Boolean,
    val lastSeenAt: String?,
    val lastSyncAt: String?,
    val rulesJson: String,
    val usageTodayJson: String,
    val cachedAtEpochMillis: Long,
)
