package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.db.tables.DeviceStatusValues
import com.familyguard.backend.db.tables.DevicesTable
import com.familyguard.backend.db.tables.UsageRecordsTable
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.UsageEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Proves the GREATEST-upsert (docs/architecture.md / database-schema.md) is idempotent:
 * replaying the same batch twice must not change the stored value beyond what applying it
 * once did, and an out-of-order (smaller, older) delivery must never regress the stored max.
 */
class UsageServiceTest {

    private val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
    private val authService = AuthService(jwtConfig)
    private val childService = ChildService()
    private val usageService = UsageService()

    private val packageName = "com.google.android.youtube"
    private val usageDate = "2026-08-16"

    private fun newParentChildDevice(): Pair<UUID, UUID> {
        val signup = authService.signup(SignupRequest("usage-test-${UUID.randomUUID()}@example.com", "password123", "Parent"))
        val parentId = UUID.fromString(signup.parentId)
        val child = childService.createChild(parentId, CreateChildRequest("Kid", 2015))
        val childId = UUID.fromString(child.id)
        val deviceId = transaction {
            DevicesTable.insertAndGetId {
                it[DevicesTable.childId] = childId
                it[status] = DeviceStatusValues.APPROVED
            }.value
        }
        return childId to deviceId
    }

    private fun storedDuration(childId: UUID, date: String): Int? = transaction {
        UsageRecordsTable.selectAll()
            .where { (UsageRecordsTable.childId eq childId) and (UsageRecordsTable.usageDate eq java.time.LocalDate.parse(date)) }
            .singleOrNull()
            ?.get(UsageRecordsTable.durationMinutes)
    }

    @Test
    fun `syncing an event once stores its duration`() {
        val (childId, deviceId) = newParentChildDevice()
        val response = usageService.syncUsage(deviceId, childId, listOf(UsageEvent(packageName, usageDate, 32)))

        assertEquals(1, response.acceptedCount)
        assertEquals(32, storedDuration(childId, usageDate))
    }

    @Test
    fun `replaying the exact same batch twice is idempotent`() {
        val (childId, deviceId) = newParentChildDevice()
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent(packageName, usageDate, 32)))
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent(packageName, usageDate, 32)))

        assertEquals(32, storedDuration(childId, usageDate))
    }

    @Test
    fun `a later, larger cumulative value replaces the stored one`() {
        val (childId, deviceId) = newParentChildDevice()
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent(packageName, usageDate, 10)))
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent(packageName, usageDate, 25)))

        assertEquals(25, storedDuration(childId, usageDate))
    }

    @Test
    fun `an out-of-order smaller value never regresses the stored maximum`() {
        val (childId, deviceId) = newParentChildDevice()
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent(packageName, usageDate, 40)))
        // A stale/queued batch from earlier in the day, delivered late, with a smaller cumulative total.
        usageService.syncUsage(deviceId, childId, listOf(UsageEvent(packageName, usageDate, 15)))

        assertEquals(40, storedDuration(childId, usageDate))
    }

    @Test
    fun `multiple events in one batch are all accepted`() {
        val (childId, deviceId) = newParentChildDevice()
        val response = usageService.syncUsage(
            deviceId,
            childId,
            listOf(
                UsageEvent("com.app.one", usageDate, 5),
                UsageEvent("com.app.two", usageDate, 12),
            ),
        )
        assertEquals(2, response.acceptedCount)
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
