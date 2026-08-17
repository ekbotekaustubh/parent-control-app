package com.familyguard.child.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of one schedule, synced from `GET /device/config`'s `schedules` field
 * (unfiltered - see `ScheduleResponse`'s KDoc) and read by `MonitorForegroundService`,
 * which passes the current moment to `EnforcementDecider.decide()` for it to determine
 * activeness itself. `daysOfWeek`/`mode`/`startTime`/`endTime` are stored as the raw
 * strings from the wire DTO - see `RuleRepository`'s mapping into
 * `domain.EnforcementSchedule` for where they're parsed into `java.time` types.
 */
@Entity(tableName = "cached_schedules")
data class CachedScheduleEntity(
    @PrimaryKey val id: String,
    /** "block" or "allow_only" — raw string form of shared's ScheduleMode enum. */
    val mode: String,
    /** Comma-separated 3-letter codes, e.g. "MON,TUE,WED" — raw string form of shared's Weekday enum list. */
    val daysOfWeek: String,
    /** "HH:mm", device-local wall-clock. */
    val startTime: String,
    val endTime: String,
)
