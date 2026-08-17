package com.familyguard.child.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A single cached rule, as read from the local Room cache
 * (`data/local/db/CachedRuleEntity.kt`). This is a plain data class — NOT the Room entity
 * itself — so this file has zero dependency on Room/Android and stays a pure-JVM,
 * table-driven-testable unit (see `docs/testing-plan.md`: "The actual block/allow/
 * over-limit decision is extracted into a pure `EnforcementDecider` class with zero
 * Android framework dependency").
 *
 * @param category app-catalog category (`apps.category`), if the parent tagged one when
 *   setting this rule — see docs/roadmap.md's "Category-level rules".
 * @param scheduleId non-null if this rule only applies during that schedule's window
 *   (docs/roadmap.md's "Schedules", the per-app/per-day variant) — null means always.
 */
data class EnforcementRule(
    val packageName: String,
    val ruleType: RuleKind,
    val dailyLimitMinutes: Int?,
    val category: String? = null,
    val scheduleId: String? = null,
)

enum class RuleKind { BLOCK, ALLOW }

/**
 * A temporary grant from an approved access request (`docs/roadmap.md`'s "Access
 * requests"), as read from the local cache (`data/local/db/CachedOverrideEntity.kt`) —
 * already filtered to non-expired by the caller (`RuleRepository.getCachedOverrides()`),
 * so this class carries no expiry field itself; [EnforcementDecider] only ever sees
 * currently-active grants.
 */
data class EnforcementOverride(
    val packageName: String,
    val extraMinutes: Int,
)

enum class ScheduleKind { BLOCK, ALLOW_ONLY }

/**
 * A blanket time window (docs/roadmap.md's "Schedules") — bedtime/school-hours style, as
 * opposed to [EnforcementRule.scheduleId]'s per-app variant. Unlike [EnforcementOverride],
 * these are passed to [EnforcementDecider] **unfiltered** ([isActiveAt] does the filtering
 * itself, given the current moment) - the backend sends every schedule for the child
 * (`ScheduleResponse`'s KDoc explains why: it doesn't know the device's timezone, so the
 * device has to be the one deciding "is this active right now").
 */
data class EnforcementSchedule(
    val id: String,
    val mode: ScheduleKind,
    val daysOfWeek: Set<DayOfWeek>,
    val startTime: LocalTime,
    val endTime: LocalTime,
) {
    /** `startTime > endTime` is a valid overnight window (e.g. 21:00-07:00) spanning into the next calendar day. */
    fun isActiveAt(now: LocalDateTime): Boolean {
        val today = now.dayOfWeek
        val time = now.toLocalTime()
        return when {
            startTime < endTime -> today in daysOfWeek && time >= startTime && time < endTime
            startTime > endTime -> (today in daysOfWeek && time >= startTime) || (today.minus(1) in daysOfWeek && time < endTime)
            else -> false // zero-length window; the backend rejects creating one, so this shouldn't occur.
        }
    }
}

/**
 * Outcome of an enforcement check for one foreground-app observation.
 */
sealed class EnforcementResult {
    /** No rule matched, or an "allow" rule matched and today's usage is within any limit. */
    data object Allowed : EnforcementResult()

    /** A standing "block" rule matched this package (or a blanket schedule is active). */
    data class Blocked(val packageName: String, val reason: String) : EnforcementResult()

    /** An "allow" rule matched, but today's accrued usage has reached/exceeded its daily limit. */
    data class LimitExceeded(
        val packageName: String,
        val reason: String,
        val dailyLimitMinutes: Int,
        val usedMinutes: Int,
    ) : EnforcementResult()
}

/**
 * Pure decision function: given the locally cached rule set, today's accrued per-app usage,
 * and the package currently in the foreground, decide whether that app should be allowed or
 * covered by the restriction screen.
 *
 * Deliberately contains ZERO Android imports (no `UsageStatsManager`, no `Context`, no
 * `android.*` of any kind) so it can be unit-tested with plain JUnit, no instrumentation,
 * no emulator — see `EnforcementDeciderTest.kt` and `docs/testing-plan.md`. `java.time` is
 * fine (plain JDK, already used elsewhere e.g. `UsageRepository`). Keep it that way: any
 * future logic that needs Android APIs belongs in the caller
 * (`service/MonitorForegroundService.kt`), not here.
 */
class EnforcementDecider {

    /**
     * Resolution order, most to least specific:
     * 1. A `BLOCK`-mode active schedule blocks everything, full stop.
     * 2. An `ALLOW_ONLY`-mode active schedule blocks everything except packages with a
     *    standing ALLOW rule (its own limit/override/category logic still applies below).
     * 3. A schedule-tied rule ([EnforcementRule.scheduleId]) only counts if that schedule
     *    is currently active; otherwise it's treated as no rule at all.
     * 4. The rule's own `dailyLimitMinutes`, if set, always wins over its category's limit.
     * 5. [activeOverrides] adds on top of whatever limit was resolved by 1-4 (never
     *    creates a limit where the app was otherwise fully allowed).
     *
     * @param cachedRules the full locally cached rule set (from Room, already mapped to
     *   plain [EnforcementRule] values by the caller)
     * @param todayUsageMinutes today's accrued foreground minutes per package, from the
     *   local usage ledger
     * @param foregroundPackage the package name currently in the foreground
     * @param activeOverrides currently-active access-request grants (already
     *   expiry-filtered by the caller)
     * @param categoryLimits this child's category daily limits (category -> minutes) -
     *   only consulted when the matching rule is ALLOW with no explicit
     *   `dailyLimitMinutes` of its own (docs/database-schema.md's `category_rules` scope
     *   note)
     * @param schedules every schedule for this child, unfiltered - [isActiveAt] decides
     *   activeness against [now]
     * @param now the device's current local date/time, for schedule-activeness checks
     */
    fun decide(
        cachedRules: List<EnforcementRule>,
        todayUsageMinutes: Map<String, Int>,
        foregroundPackage: String,
        activeOverrides: List<EnforcementOverride> = emptyList(),
        categoryLimits: Map<String, Int> = emptyMap(),
        schedules: List<EnforcementSchedule> = emptyList(),
        now: LocalDateTime = LocalDateTime.now(),
    ): EnforcementResult {
        val activeSchedules = schedules.filter { it.isActiveAt(now) }
        val activeScheduleIds = activeSchedules.map { it.id }.toSet()

        val candidateRule = cachedRules.firstOrNull { it.packageName == foregroundPackage }
        val rule = candidateRule?.takeIf { it.scheduleId == null || it.scheduleId in activeScheduleIds }

        if (activeSchedules.any { it.mode == ScheduleKind.BLOCK }) {
            return EnforcementResult.Blocked(foregroundPackage, "This app is restricted right now.")
        }
        val allowOnlyActive = activeSchedules.any { it.mode == ScheduleKind.ALLOW_ONLY }
        if (allowOnlyActive && (rule == null || rule.ruleType != RuleKind.ALLOW)) {
            return EnforcementResult.Blocked(foregroundPackage, "Only approved apps are available right now.")
        }

        if (rule == null) return EnforcementResult.Allowed

        val overrideMinutes = activeOverrides.firstOrNull { it.packageName == foregroundPackage }?.extraMinutes ?: 0

        return when (rule.ruleType) {
            RuleKind.BLOCK -> {
                if (overrideMinutes <= 0) {
                    EnforcementResult.Blocked(
                        packageName = foregroundPackage,
                        reason = "This app is restricted.",
                    )
                } else {
                    checkAgainstLimit(foregroundPackage, todayUsageMinutes, limit = overrideMinutes)
                }
            }

            RuleKind.ALLOW -> {
                val categoryLimit = rule.category?.let { categoryLimits[it] }
                val baseLimit = rule.dailyLimitMinutes ?: categoryLimit
                if (baseLimit == null) {
                    EnforcementResult.Allowed
                } else {
                    checkAgainstLimit(foregroundPackage, todayUsageMinutes, limit = baseLimit + overrideMinutes)
                }
            }
        }
    }

    private fun checkAgainstLimit(foregroundPackage: String, todayUsageMinutes: Map<String, Int>, limit: Int): EnforcementResult {
        val used = todayUsageMinutes[foregroundPackage] ?: 0
        return if (used >= limit) {
            EnforcementResult.LimitExceeded(
                packageName = foregroundPackage,
                reason = "Today's time limit for this app has been reached.",
                dailyLimitMinutes = limit,
                usedMinutes = used,
            )
        } else {
            EnforcementResult.Allowed
        }
    }
}
