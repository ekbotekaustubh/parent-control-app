package com.familyguard.child.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Table-driven tests for the single most important class in this app (per
 * `docs/testing-plan.md`) — pure JVM, no Android/instrumentation needed at all.
 */
class EnforcementDeciderTest {

    private val decider = EnforcementDecider()

    @Test
    fun `no rule for the foreground app is allowed`() {
        val result = decider.decide(
            cachedRules = emptyList(),
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.example.unrestricted",
        )
        assertEquals(EnforcementResult.Allowed, result)
    }

    @Test
    fun `blocked app is blocked regardless of usage`() {
        val rules = listOf(EnforcementRule("com.evil.app", RuleKind.BLOCK, dailyLimitMinutes = null))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.evil.app",
        )
        assertTrue(result is EnforcementResult.Blocked)
        assertEquals("com.evil.app", (result as EnforcementResult.Blocked).packageName)
    }

    @Test
    fun `allow rule under limit is allowed`() {
        val rules = listOf(EnforcementRule("com.google.android.youtube", RuleKind.ALLOW, dailyLimitMinutes = 45))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.google.android.youtube" to 30),
            foregroundPackage = "com.google.android.youtube",
        )
        assertEquals(EnforcementResult.Allowed, result)
    }

    @Test
    fun `allow rule at exactly the limit is limit-exceeded`() {
        val rules = listOf(EnforcementRule("com.google.android.youtube", RuleKind.ALLOW, dailyLimitMinutes = 45))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.google.android.youtube" to 45),
            foregroundPackage = "com.google.android.youtube",
        )
        assertTrue(result is EnforcementResult.LimitExceeded)
        val exceeded = result as EnforcementResult.LimitExceeded
        assertEquals(45, exceeded.dailyLimitMinutes)
        assertEquals(45, exceeded.usedMinutes)
    }

    @Test
    fun `allow rule over the limit is limit-exceeded`() {
        val rules = listOf(EnforcementRule("com.google.android.youtube", RuleKind.ALLOW, dailyLimitMinutes = 45))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.google.android.youtube" to 60),
            foregroundPackage = "com.google.android.youtube",
        )
        assertTrue(result is EnforcementResult.LimitExceeded)
    }

    @Test
    fun `allow rule with no daily limit is always allowed`() {
        val rules = listOf(EnforcementRule("com.example.unlimited", RuleKind.ALLOW, dailyLimitMinutes = null))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.example.unlimited" to 500),
            foregroundPackage = "com.example.unlimited",
        )
        assertEquals(EnforcementResult.Allowed, result)
    }

    @Test
    fun `usage for other packages does not affect the foreground package's decision`() {
        val rules = listOf(EnforcementRule("com.example.a", RuleKind.ALLOW, dailyLimitMinutes = 10))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.example.b" to 999),
            foregroundPackage = "com.example.a",
        )
        assertEquals(EnforcementResult.Allowed, result)
    }

    @Test
    fun `missing usage entry for an allow rule defaults to zero used minutes`() {
        val rules = listOf(EnforcementRule("com.example.a", RuleKind.ALLOW, dailyLimitMinutes = 10))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.example.a",
        )
        assertEquals(EnforcementResult.Allowed, result)
    }

    @Test
    fun `an active override on a blocked app allows it up to the granted minutes`() {
        val rules = listOf(EnforcementRule("com.evil.app", RuleKind.BLOCK, dailyLimitMinutes = null))
        val overrides = listOf(EnforcementOverride("com.evil.app", extraMinutes = 15))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.evil.app" to 10),
            foregroundPackage = "com.evil.app",
            activeOverrides = overrides,
        )
        assertEquals(EnforcementResult.Allowed, result)
    }

    @Test
    fun `a blocked app reverts to blocked once the override's granted minutes are used up`() {
        val rules = listOf(EnforcementRule("com.evil.app", RuleKind.BLOCK, dailyLimitMinutes = null))
        val overrides = listOf(EnforcementOverride("com.evil.app", extraMinutes = 15))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.evil.app" to 15),
            foregroundPackage = "com.evil.app",
            activeOverrides = overrides,
        )
        assertTrue(result is EnforcementResult.LimitExceeded)
        assertEquals(15, (result as EnforcementResult.LimitExceeded).dailyLimitMinutes)
    }

    @Test
    fun `an active override on an allow rule raises the effective limit`() {
        val rules = listOf(EnforcementRule("com.google.android.youtube", RuleKind.ALLOW, dailyLimitMinutes = 45))
        val overrides = listOf(EnforcementOverride("com.google.android.youtube", extraMinutes = 15))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.google.android.youtube" to 50),
            foregroundPackage = "com.google.android.youtube",
            activeOverrides = overrides,
        )
        assertEquals(EnforcementResult.Allowed, result)
    }

    @Test
    fun `an override for a different package does not affect this one`() {
        val rules = listOf(EnforcementRule("com.evil.app", RuleKind.BLOCK, dailyLimitMinutes = null))
        val overrides = listOf(EnforcementOverride("com.other.app", extraMinutes = 15))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.evil.app",
            activeOverrides = overrides,
        )
        assertTrue(result is EnforcementResult.Blocked)
    }

    // ---- category-level limits ----

    @Test
    fun `a category limit is used when the rule has no explicit daily limit`() {
        val rules = listOf(EnforcementRule("com.example.a", RuleKind.ALLOW, dailyLimitMinutes = null, category = "social"))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.example.a" to 60),
            foregroundPackage = "com.example.a",
            categoryLimits = mapOf("social" to 45),
        )
        assertTrue(result is EnforcementResult.LimitExceeded)
        assertEquals(45, (result as EnforcementResult.LimitExceeded).dailyLimitMinutes)
    }

    @Test
    fun `an explicit per-app limit wins over its category's limit`() {
        val rules = listOf(EnforcementRule("com.example.a", RuleKind.ALLOW, dailyLimitMinutes = 90, category = "social"))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.example.a" to 60),
            foregroundPackage = "com.example.a",
            categoryLimits = mapOf("social" to 45),
        )
        assertEquals(EnforcementResult.Allowed, result) // 60 < 90 (the per-app limit), category's 45 is ignored
    }

    @Test
    fun `a category limit does not apply to a block rule`() {
        val rules = listOf(EnforcementRule("com.example.a", RuleKind.BLOCK, dailyLimitMinutes = null, category = "social"))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.example.a",
            categoryLimits = mapOf("social" to 45),
        )
        assertTrue(result is EnforcementResult.Blocked)
    }

    // ---- blanket schedules ----

    private fun blockSchedule(days: Set<DayOfWeek>, start: String, end: String) = EnforcementSchedule(
        id = "sched-1",
        mode = ScheduleKind.BLOCK,
        daysOfWeek = days,
        startTime = LocalTime.parse(start),
        endTime = LocalTime.parse(end),
    )

    @Test
    fun `a BLOCK schedule active right now blocks even an unruled app`() {
        val now = LocalDateTime.of(2024, 1, 1, 22, 0) // Monday 22:00
        val schedules = listOf(blockSchedule(setOf(DayOfWeek.MONDAY), "21:00", "23:00"))
        val result = decider.decide(
            cachedRules = emptyList(),
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.example.a",
            schedules = schedules,
            now = now,
        )
        assertTrue(result is EnforcementResult.Blocked)
    }

    @Test
    fun `a BLOCK schedule outside its window does not block`() {
        val now = LocalDateTime.of(2024, 1, 1, 12, 0) // Monday noon
        val schedules = listOf(blockSchedule(setOf(DayOfWeek.MONDAY), "21:00", "23:00"))
        val result = decider.decide(
            cachedRules = emptyList(),
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.example.a",
            schedules = schedules,
            now = now,
        )
        assertEquals(EnforcementResult.Allowed, result)
    }

    @Test
    fun `an overnight BLOCK schedule stays active past midnight`() {
        val now = LocalDateTime.of(2024, 1, 2, 3, 0) // Tuesday 03:00 - within Monday night's window
        val schedules = listOf(blockSchedule(setOf(DayOfWeek.MONDAY), "21:00", "07:00"))
        val result = decider.decide(
            cachedRules = emptyList(),
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.example.a",
            schedules = schedules,
            now = now,
        )
        assertTrue(result is EnforcementResult.Blocked)
    }

    @Test
    fun `an ALLOW_ONLY schedule blocks an app with no rule`() {
        val now = LocalDateTime.of(2024, 1, 1, 10, 0) // Monday 10:00
        val schedules = listOf(
            EnforcementSchedule("sched-1", ScheduleKind.ALLOW_ONLY, setOf(DayOfWeek.MONDAY), LocalTime.parse("09:00"), LocalTime.parse("11:00")),
        )
        val result = decider.decide(
            cachedRules = emptyList(),
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.example.unruled",
            schedules = schedules,
            now = now,
        )
        assertTrue(result is EnforcementResult.Blocked)
    }

    @Test
    fun `an ALLOW_ONLY schedule lets through an app with a standing ALLOW rule`() {
        val now = LocalDateTime.of(2024, 1, 1, 10, 0) // Monday 10:00
        val schedules = listOf(
            EnforcementSchedule("sched-1", ScheduleKind.ALLOW_ONLY, setOf(DayOfWeek.MONDAY), LocalTime.parse("09:00"), LocalTime.parse("11:00")),
        )
        val rules = listOf(EnforcementRule("com.example.calculator", RuleKind.ALLOW, dailyLimitMinutes = null))
        val result = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = emptyMap(),
            foregroundPackage = "com.example.calculator",
            schedules = schedules,
            now = now,
        )
        assertEquals(EnforcementResult.Allowed, result)
    }

    @Test
    fun `a schedule-tied ALLOW rule only counts as approved while its schedule is active`() {
        // A BLOCK-tied rule under ALLOW_ONLY would always be masked by the blanket "must be
        // ALLOW" check below, so it wouldn't isolate anything - the meaningful combination
        // (and the one the "study hours: allow only these apps" story needs) is an ALLOW
        // rule tied to an ALLOW_ONLY schedule.
        val schedule = EnforcementSchedule("sched-1", ScheduleKind.ALLOW_ONLY, setOf(DayOfWeek.MONDAY), LocalTime.parse("09:00"), LocalTime.parse("11:00"))
        val rules = listOf(EnforcementRule("com.example.calculator", RuleKind.ALLOW, dailyLimitMinutes = 20, scheduleId = schedule.id))

        // Outside the schedule's window: no schedule is active at all, so the tied rule
        // doesn't count and there's nothing else restricting this package either.
        val outsideWindow = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.example.calculator" to 999),
            foregroundPackage = "com.example.calculator",
            schedules = listOf(schedule),
            now = LocalDateTime.of(2024, 1, 1, 15, 0),
        )
        assertEquals(EnforcementResult.Allowed, outsideWindow)

        // Inside the window: the tied rule counts as this package's rule, and its own
        // 20-minute limit governs.
        val insideWindowUnderLimit = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.example.calculator" to 5),
            foregroundPackage = "com.example.calculator",
            schedules = listOf(schedule),
            now = LocalDateTime.of(2024, 1, 1, 10, 0),
        )
        assertEquals(EnforcementResult.Allowed, insideWindowUnderLimit)

        val insideWindowOverLimit = decider.decide(
            cachedRules = rules,
            todayUsageMinutes = mapOf("com.example.calculator" to 25),
            foregroundPackage = "com.example.calculator",
            schedules = listOf(schedule),
            now = LocalDateTime.of(2024, 1, 1, 10, 0),
        )
        assertTrue(insideWindowOverLimit is EnforcementResult.LimitExceeded)
    }
}
