package com.familyguard.child.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Queued cumulative usage snapshot awaiting `POST /device/usage-sync`. Each row is a
 * cumulative (packageName, usageDate) total, not a delta — matching the backend's
 * `GREATEST`-upsert design (see `docs/database-schema.md`'s `usage_records` table and
 * `docs/architecture.md`'s usage-reporting data flow), so replaying this queue after a
 * period offline needs no client-side dedup: resending an already-applied cumulative value
 * is a no-op server-side.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val usageDate: String,
    val durationMinutes: Int,
    val createdAt: Long,
    val attempts: Int = 0,
)
