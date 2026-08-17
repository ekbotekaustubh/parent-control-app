package com.familyguard.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceStatusInfo(
    val status: String,
    val online: Boolean,
    val lastSeenAt: String?,
    val lastSyncAt: String?,
)

@Serializable
data class UsageTodayItem(
    val packageName: String,
    val displayName: String?,
    val durationMinutes: Int,
    val dailyLimitMinutes: Int?,
    val isOverLimit: Boolean,
)

@Serializable
data class DashboardResponse(
    val device: DeviceStatusInfo,
    val rules: List<AppRuleResponse>,
    val usageToday: List<UsageTodayItem>,
)
