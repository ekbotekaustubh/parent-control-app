package com.familyguard.shared.dto

import com.familyguard.shared.enums.DeviceStatus
import kotlinx.serialization.Serializable

@Serializable
data class PairingCodeResponse(
    val code: String,
    val expiresAt: String,
)

@Serializable
data class ClaimRequest(
    val code: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
)

@Serializable
data class ClaimResponse(
    val deviceId: String,
    val claimTicket: String,
    val claimTicketExpiresIn: Long,
)

@Serializable
data class ClaimStatusResponse(
    val status: DeviceStatus,
)

@Serializable
data class TokenExchangeRequest(
    val claimTicket: String,
)

@Serializable
data class DeviceTokensResponse(
    val deviceAccessToken: String,
    val deviceRefreshToken: String,
    val expiresIn: Long,
)

@Serializable
data class DeviceTokenRefreshRequest(
    val refreshToken: String,
)

@Serializable
data class DeviceTokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)
