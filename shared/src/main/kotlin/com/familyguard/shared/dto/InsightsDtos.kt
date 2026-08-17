package com.familyguard.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class TopAppUsage(
    val packageName: String,
    val displayName: String?,
    val minutes: Int,
)

/**
 * Purely a read-side rollup over `usage_records` (docs/roadmap.md's "Smart insights") - no
 * new tables. "This week"/"last week" are rolling 7-day windows ending today, not
 * calendar weeks, for the same reason `access_overrides` uses a rolling 24h window: the
 * backend has no reliable device-local-timezone signal to compute a real calendar boundary.
 */
@Serializable
data class InsightsResponse(
    val currentWeekMinutes: Int,
    val previousWeekMinutes: Int,
    /** Null when previousWeekMinutes is 0 - no baseline to compute a percentage against. */
    val weekOverWeekChangePercent: Double?,
    val topApps: List<TopAppUsage>,
    val generatedAt: String,
)
