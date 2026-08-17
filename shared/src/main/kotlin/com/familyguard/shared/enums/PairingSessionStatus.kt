package com.familyguard.shared.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Server-internal pairing_sessions.status values — not returned to clients directly. */
@Serializable
enum class PairingSessionStatus {
    @SerialName("pending") PENDING,
    @SerialName("claimed") CLAIMED,
    @SerialName("expired") EXPIRED,
    @SerialName("revoked") REVOKED,
}
