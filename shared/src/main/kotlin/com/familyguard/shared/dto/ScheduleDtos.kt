package com.familyguard.shared.dto

import com.familyguard.shared.enums.ScheduleMode
import com.familyguard.shared.enums.Weekday
import kotlinx.serialization.Serializable

@Serializable
data class UpsertScheduleRequest(
    val name: String,
    val daysOfWeek: List<Weekday>,
    /** "HH:mm", 24h, device-local wall-clock time - see ScheduleResponse's KDoc on why these are never converted server-side. */
    val startTime: String,
    val endTime: String,
    val mode: ScheduleMode,
)

/**
 * `startTime`/`endTime` are plain "HH:mm" wall-clock values with no timezone attached, by
 * design: they mean "9pm in whatever timezone the child's device is in", not a specific UTC
 * instant, so the device (not the backend) is what evaluates "is this schedule active right
 * now" - see the child app's `EnforcementDecider`. `startTime > endTime` is a valid,
 * supported overnight window (e.g. 21:00-07:00 for bedtime).
 */
@Serializable
data class ScheduleResponse(
    val id: String,
    val childId: String,
    val name: String,
    val daysOfWeek: List<Weekday>,
    val startTime: String,
    val endTime: String,
    val mode: ScheduleMode,
    val isActive: Boolean,
)
