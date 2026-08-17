package com.familyguard.shared.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Weekday {
    @SerialName("MON") MON,
    @SerialName("TUE") TUE,
    @SerialName("WED") WED,
    @SerialName("THU") THU,
    @SerialName("FRI") FRI,
    @SerialName("SAT") SAT,
    @SerialName("SUN") SUN,
}
