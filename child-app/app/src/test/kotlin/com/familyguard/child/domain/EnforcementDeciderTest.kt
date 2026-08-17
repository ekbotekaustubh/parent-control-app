package com.familyguard.child.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
