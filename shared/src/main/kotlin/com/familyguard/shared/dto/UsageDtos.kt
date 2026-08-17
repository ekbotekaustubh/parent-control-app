package com.familyguard.shared.dto

import kotlinx.serialization.Serializable

/** durationMinutes is the device's CUMULATIVE total for that app+date, not a delta. */
@Serializable
data class UsageEvent(
    val packageName: String,
    val usageDate: String, // yyyy-MM-dd, child device's local day
    val durationMinutes: Int,
)

@Serializable
data class UsageSyncRequest(
    val events: List<UsageEvent>,
)

@Serializable
data class UsageSyncResponse(
    val acceptedCount: Int,
    val serverTimeUtc: String,
)
