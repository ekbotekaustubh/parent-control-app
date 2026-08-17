package com.familyguard.parent.ui.dashboard

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Small formatting helper — not business logic, so it lives next to the screen that uses it. */
fun relativeTimeLabel(iso: String?): String {
    if (iso == null) return "Never"
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return "Unknown"
    val now = Clock.System.now()
    val minutes = (now - instant).inWholeMinutes
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}
