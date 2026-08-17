package com.familyguard.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReportAppUsage(
    val packageName: String,
    val displayName: String?,
    val totalMinutes: Int,
)

@Serializable
data class ReportDayUsage(
    val date: String,
    val totalMinutes: Int,
)

/** `range` is one of "daily", "weekly", "monthly" - see `ApiPaths.CHILD_REPORT`'s query params in docs/api-spec.md. */
@Serializable
data class ReportResponse(
    val range: String,
    val startDate: String,
    val endDate: String,
    val totalMinutes: Int,
    val byApp: List<ReportAppUsage>,
    val byDay: List<ReportDayUsage>,
)
