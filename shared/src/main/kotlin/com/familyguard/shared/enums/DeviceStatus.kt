package com.familyguard.shared.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Also reused as the pairing-claim poll status (`GET /pairing/claim/{deviceId}/status`) —
 * the value space is identical (pending_approval | approved | revoked).
 */
@Serializable
enum class DeviceStatus {
    @SerialName("pending_approval") PENDING_APPROVAL,
    @SerialName("approved") APPROVED,
    @SerialName("revoked") REVOKED,
}
