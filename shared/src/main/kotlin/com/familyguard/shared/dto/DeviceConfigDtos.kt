package com.familyguard.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceConfigResponse(
    val childId: String,
    val rules: List<AppRuleResponse>,
    /** Active (not-yet-expired) grants from approved access requests — see `AccessOverrideResponse`. */
    val overrides: List<AccessOverrideResponse>,
    /** This child's category-level fallback limits — see `docs/roadmap.md`'s "Category-level rules". */
    val categoryRules: List<CategoryRuleResponse>,
    /** This child's schedules, unfiltered — the device evaluates "is this one active right now" itself, using its own local clock (see `ScheduleResponse`'s KDoc). */
    val schedules: List<ScheduleResponse>,
    val configVersion: Long,
    val syncIntervalSeconds: Long,
    val serverTimeUtc: String,
)

/**
 * A temporary grant on top of the standing rule for one app, from an approved access
 * request (`docs/roadmap.md`'s "Access requests"). Not itself a rule — `app_rules` and
 * `children.config_version` are untouched by resolving a request (see
 * `docs/database-schema.md`'s `access_overrides` table) — the child app's
 * `EnforcementDecider` combines this with the standing rule at decision time.
 */
@Serializable
data class AccessOverrideResponse(
    val packageName: String,
    val extraMinutes: Int,
    val expiresAt: String,
)

@Serializable
data class HeartbeatResponse(
    val ok: Boolean,
    val configVersion: Long,
)
