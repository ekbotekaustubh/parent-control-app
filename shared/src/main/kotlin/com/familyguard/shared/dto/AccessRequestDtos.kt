package com.familyguard.shared.dto

import com.familyguard.shared.enums.AccessRequestStatus
import kotlinx.serialization.Serializable

/** Device-authenticated; childId/deviceId come from the JWT, never from this body. */
@Serializable
data class CreateAccessRequestRequest(
    val packageName: String,
    val requestedMinutes: Int,
)

@Serializable
data class ResolveAccessRequestRequest(
    val approve: Boolean,
    /** Only meaningful when approve = true; defaults to the originally requested minutes if omitted. */
    val resolvedMinutes: Int? = null,
)

@Serializable
data class AccessRequestResponse(
    val id: String,
    val childId: String,
    val packageName: String,
    val displayName: String?,
    val requestedMinutes: Int,
    val status: AccessRequestStatus,
    val resolvedMinutes: Int?,
    val createdAt: String,
    val resolvedAt: String?,
)
