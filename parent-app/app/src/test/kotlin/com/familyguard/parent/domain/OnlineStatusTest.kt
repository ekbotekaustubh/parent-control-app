package com.familyguard.parent.domain

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineStatusTest {

    private val now = Instant.parse("2026-08-16T12:00:00Z")

    @Test
    fun `null lastSeenAt is unknown`() {
        assertEquals(OnlineStatus.UNKNOWN, OnlineStatusCalculator.compute(null, now))
    }

    @Test
    fun `just now is online`() {
        assertEquals(OnlineStatus.ONLINE, OnlineStatusCalculator.compute(now, now))
    }

    @Test
    fun `5 minutes ago is online`() {
        val lastSeen = now - 5.minutes
        assertEquals(OnlineStatus.ONLINE, OnlineStatusCalculator.compute(lastSeen, now))
    }

    @Test
    fun `exactly at the 10 minute threshold is still online`() {
        val lastSeen = now - 10.minutes
        assertEquals(OnlineStatus.ONLINE, OnlineStatusCalculator.compute(lastSeen, now))
    }

    @Test
    fun `just past the 10 minute threshold is offline`() {
        val lastSeen = now - 10.minutes - 1.seconds
        assertEquals(OnlineStatus.OFFLINE, OnlineStatusCalculator.compute(lastSeen, now))
    }

    @Test
    fun `1 hour ago is offline`() {
        val lastSeen = now - 60.minutes
        assertEquals(OnlineStatus.OFFLINE, OnlineStatusCalculator.compute(lastSeen, now))
    }

    @Test
    fun `future lastSeenAt (clock skew) is treated as online`() {
        val lastSeen = now + 1.minutes
        assertEquals(OnlineStatus.ONLINE, OnlineStatusCalculator.compute(lastSeen, now))
    }
}
