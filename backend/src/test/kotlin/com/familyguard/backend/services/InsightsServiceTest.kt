package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.db.tables.DeviceStatusValues
import com.familyguard.backend.db.tables.DevicesTable
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.UsageEvent
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class InsightsServiceTest {

    private val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
    private val authService = AuthService(jwtConfig)
    private val childService = ChildService()
    private val usageService = UsageService()
    private val insightsService = InsightsService()

    private fun newParentChildDevice(): Triple<UUID, UUID, UUID> {
        val signup = authService.signup(SignupRequest("insights-test-${UUID.randomUUID()}@example.com", "password123", "Parent"))
        val parentId = UUID.fromString(signup.parentId)
        val child = childService.createChild(parentId, CreateChildRequest("Kid", 2015))
        val childId = UUID.fromString(child.id)
        val deviceId = transaction {
            DevicesTable.insertAndGetId {
                it[DevicesTable.childId] = childId
                it[status] = DeviceStatusValues.APPROVED
            }.value
        }
        return Triple(parentId, childId, deviceId)
    }

    @Test
    fun `with no usage history at all, both weeks are zero and change percent is null`() {
        val (parentId, childId, _) = newParentChildDevice()

        val insights = insightsService.getInsights(parentId, childId)

        assertEquals(0, insights.currentWeekMinutes)
        assertEquals(0, insights.previousWeekMinutes)
        assertNull(insights.weekOverWeekChangePercent)
        assertTrue(insights.topApps.isEmpty())
    }

    @Test
    fun `week-over-week change percent reflects the increase between the two rolling windows`() {
        val (parentId, childId, deviceId) = newParentChildDevice()
        val today = LocalDate.now()
        // Previous week: 8-13 days ago (outside the current 7-day window, inside the prior one).
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent("com.example.a", today.minusDays(10).toString(), 50)))
        // Current week: today.
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent("com.example.a", today.toString(), 100)))

        val insights = insightsService.getInsights(parentId, childId)

        assertEquals(100, insights.currentWeekMinutes)
        assertEquals(50, insights.previousWeekMinutes)
        assertEquals(100.0, insights.weekOverWeekChangePercent!!, 0.001)
    }

    @Test
    fun `topApps ranks by minutes this week, capped at 3`() {
        val (parentId, childId, deviceId) = newParentChildDevice()
        val today = LocalDate.now().toString()
        usageService.syncUsage(
            deviceId,
            childId,
            listOf(
                UsageEvent("com.example.a", today, 40),
                UsageEvent("com.example.b", today, 30),
                UsageEvent("com.example.c", today, 20),
                UsageEvent("com.example.d", today, 10),
            ),
        )

        val insights = insightsService.getInsights(parentId, childId)

        assertEquals(3, insights.topApps.size)
        assertEquals("com.example.a", insights.topApps.first().packageName)
        assertEquals(40, insights.topApps.first().minutes)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpDatabase() {
            DatabaseFactory.init(
                jdbcUrl = "jdbc:postgresql://localhost:5432/familyguard",
                user = "familyguard",
                password = "familyguard",
            )
        }
    }
}
