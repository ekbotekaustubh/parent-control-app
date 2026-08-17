package com.familyguard.shared.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RuleType {
    @SerialName("block") BLOCK,
    @SerialName("allow") ALLOW,
}
