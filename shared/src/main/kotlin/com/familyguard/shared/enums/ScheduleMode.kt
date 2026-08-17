package com.familyguard.shared.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScheduleMode {
    /** Every app is blocked while the schedule is active - a bedtime/lockdown window. */
    @SerialName("block") BLOCK,

    /** Every app is blocked while active EXCEPT ones with a standing ALLOW app_rules row - a school-hours/focus window. */
    @SerialName("allow_only") ALLOW_ONLY,
}
