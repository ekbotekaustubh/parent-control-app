package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.db.tables.DeviceStatusValues
import com.familyguard.backend.db.tables.DevicesTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.UsageEvent
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class ReportServiceTest {

    private val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
    private val authService = AuthService(jwtConfig)
    private val childService = ChildService()
    private val usageService = UsageService()
    private val reportService = ReportService()

    private fun newParentChildDevice(): Triple<UUID, UUID, UUID> {
        val signup = authService.signup(SignupRequest("report-test-${UUID.randomUUID()}@example.com", "password123", "Parent"))
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
    fun `daily report totals only today's usage`() {
        val (parentId, childId, deviceId) = newParentChildDevice()
        val today = LocalDate.now()
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent("com.example.a", today.toString(), 20)))
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent("com.example.a", today.minusDays(3).toString(), 999)))

        val report = reportService.getReport(parentId, childId, "daily", today)

        assertEquals(20, report.totalMinutes)
        assertEquals(today.toString(), report.startDate)
        assertEquals(today.toString(), report.endDate)
    }

    @Test
    fun `weekly report sums usage across the last 7 days and breaks it down by app`() {
        val (parentId, childId, deviceId) = newParentChildDevice()
        val today = LocalDate.now()
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent("com.example.a", today.toString(), 10)))
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent("com.example.b", today.minusDays(2).toString(), 15)))
        // Outside the 7-day window entirely.
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent("com.example.a", today.minusDays(10).toString(), 999)))

        val report = reportService.getReport(parentId, childId, "weekly", today)

        assertEquals(25, report.totalMinutes)
        assertEquals(2, report.byApp.size)
        assertEquals(10, report.byApp.single { it.packageName == "com.example.a" }.totalMinutes)
    }

    @Test
    fun `an unknown range is rejected`() {
        val (parentId, childId, _) = newParentChildDevice()

        val ex = assertThrows(ApiException.BadRequest::class.java) {
            reportService.getReport(parentId, childId, "yearly", LocalDate.now())
        }
        assertEquals("INVALID_RANGE", ex.code)
    }

    @Test
    fun `a non-owning parent gets NOT_FOUND`() {
        val (_, childId, _) = newParentChildDevice()
        val (otherParentId, _, _) = newParentChildDevice()

        val ex = assertThrows(ApiException.NotFound::class.java) {
            reportService.getReport(otherParentId, childId, "daily", LocalDate.now())
        }
        assertEquals("CHILD_NOT_FOUND", ex.code)
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
