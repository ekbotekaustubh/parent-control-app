package com.familyguard.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceConfigResponse(
    val childId: String,
    val rules: List<AppRuleResponse>,
    val configVersion: Long,
    val syncIntervalSeconds: Long,
    val serverTimeUtc: String,
)

@Serializable
data class HeartbeatResponse(
    val ok: Boolean,
    val configVersion: Long,
)
