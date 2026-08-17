package com.familyguard.child.domain

/**
 * A single cached rule, as read from the local Room cache
 * (`data/local/db/CachedRuleEntity.kt`). This is a plain data class — NOT the Room entity
 * itself — so this file has zero dependency on Room/Android and stays a pure-JVM,
 * table-driven-testable unit (see `docs/testing-plan.md`: "The actual block/allow/
 * over-limit decision is extracted into a pure `EnforcementDecider` class with zero
 * Android framework dependency").
 */
data class EnforcementRule(
    val packageName: String,
    val ruleType: RuleKind,
    val dailyLimitMinutes: Int?,
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

/**
 * Outcome of an enforcement check for one foreground-app observation.
 */
sealed class EnforcementResult {
    /** No rule matched, or an "allow" rule matched and today's usage is within any limit. */
    data object Allowed : EnforcementResult()

    /** A standing "block" rule matched this package. */
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
 * no emulator — see `EnforcementDeciderTest.kt` and `docs/testing-plan.md`. Keep it that way:
 * any future logic that needs Android APIs belongs in the caller
 * (`service/MonitorForegroundService.kt`), not here.
 */
class EnforcementDecider {

    /**
     * @param cachedRules the full locally cached rule set (from Room, already mapped to
     *   plain [EnforcementRule] values by the caller)
     * @param todayUsageMinutes today's accrued foreground minutes per package, from the
     *   local usage ledger
     * @param foregroundPackage the package name currently in the foreground
     * @param activeOverrides currently-active access-request grants (already
     *   expiry-filtered by the caller). An override's `extraMinutes` is added on top of the
     *   standing rule's limit — for a BLOCK rule that means "allowed up to extraMinutes
     *   minutes today, then blocked again"; for an ALLOW rule with a limit, it raises that
     *   limit by extraMinutes. Overrides never affect an ALLOW rule with no limit (already
     *   unrestricted) or a package with no standing rule at all (nothing to override).
     */
    fun decide(
        cachedRules: List<EnforcementRule>,
        todayUsageMinutes: Map<String, Int>,
        foregroundPackage: String,
        activeOverrides: List<EnforcementOverride> = emptyList(),
    ): EnforcementResult {
        val rule = cachedRules.firstOrNull { it.packageName == foregroundPackage }
            ?: return EnforcementResult.Allowed

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
                val limit = rule.dailyLimitMinutes
                if (limit == null) {
                    EnforcementResult.Allowed
                } else {
                    checkAgainstLimit(foregroundPackage, todayUsageMinutes, limit = limit + overrideMinutes)
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
