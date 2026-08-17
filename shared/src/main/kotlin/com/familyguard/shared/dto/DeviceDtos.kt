package com.familyguard.shared.dto

import com.familyguard.shared.enums.DeviceStatus
import kotlinx.serialization.Serializable

@Serializable
data class DeviceResponse(
    val id: String,
    val childId: String,
    val deviceName: String?,
    val model: String?,
    val osVersion: String?,
    val appVersion: String?,
    val status: DeviceStatus,
    val lastSeenAt: String?,
    val lastSyncAt: String?,
    val pairedAt: String?,
    val approvedAt: String?,
)
