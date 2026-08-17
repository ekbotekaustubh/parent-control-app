package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.db.tables.PairingSessionsTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.ClaimRequest
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.DeviceTokenRefreshRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.enums.DeviceStatus
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Unit-level coverage of the pairing-code and claim-ticket state machines and expiry
 * handling (docs/pairing-security.md). The concurrency proof for the atomic
 * `UPDATE ... WHERE status='pending'` claim (fired from genuinely simultaneous callers
 * against a real Postgres) lives in src/integrationTest/PairingConcurrencyIntegrationTest,
 * since that needs real overlapping transactions to mean anything.
 */
class PairingServiceTest {

    private val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
    private val authService = AuthService(jwtConfig)
    private val childService = ChildService()
    private val pairingService = PairingService(jwtConfig)

    private fun newParentAndChild(): Pair<UUID, UUID> {
        val signup = authService.signup(SignupRequest("pairing-test-${UUID.randomUUID()}@example.com", "password123", "Parent"))
        val parentId = UUID.fromString(signup.parentId)
        val child = childService.createChild(parentId, CreateChildRequest("Kid", 2015))
        return parentId to UUID.fromString(child.id)
    }

    @Test
    fun `generating a new code invalidates the previous pending code for that child`() {
        val (parentId, childId) = newParentAndChild()
        val first = pairingService.generateCode(parentId, childId)
        pairingService.generateCode(parentId, childId) // second code supersedes the first

        val ex = assertThrows(ApiException.NotFound::class.java) {
            pairingService.claim(ClaimRequest(first.code, "Pixel 8", "14", "1.0.0"))
        }
        assertEquals("CODE_NOT_FOUND", ex.code)
    }

    @Test
    fun `claim succeeds once and issues a claim ticket, pollStatus reports pending_approval`() {
        val (parentId, childId) = newParentAndChild()
        val code = pairingService.generateCode(parentId, childId)

        val claimed = pairingService.claim(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))
        assertNotEquals("", claimed.claimTicket)

        val status = pairingService.pollStatus(UUID.fromString(claimed.deviceId), claimed.claimTicket)
        assertEquals(DeviceStatus.PENDING_APPROVAL, status.status)
    }

    @Test
    fun `claiming an already-claimed code fails with CODE_ALREADY_CLAIMED`() {
        val (parentId, childId) = newParentAndChild()
        val code = pairingService.generateCode(parentId, childId)
        pairingService.claim(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))

        val ex = assertThrows(ApiException.Conflict::class.java) {
            pairingService.claim(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))
        }
        assertEquals("CODE_ALREADY_CLAIMED", ex.code)
    }

    @Test
    fun `claiming an unknown code fails with CODE_NOT_FOUND`() {
        val ex = assertThrows(ApiException.NotFound::class.java) {
            pairingService.claim(ClaimRequest("ZZZZZZZZ", "Pixel 8", "14", "1.0.0"))
        }
        assertEquals("CODE_NOT_FOUND", ex.code)
    }

    @Test
    fun `claiming an expired code fails with CODE_EXPIRED`() {
        val (parentId, childId) = newParentAndChild()
        val code = pairingService.generateCode(parentId, childId)

        // Simulate expiry by backdating expires_at directly (still status='pending').
        transaction {
            PairingSessionsTable.update({ PairingSessionsTable.childId eq childId }) {
                it[expiresAt] = OffsetDateTime.now().minusMinutes(1)
            }
        }

        val ex = assertThrows(ApiException.Gone::class.java) {
            pairingService.claim(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))
        }
        assertEquals("CODE_EXPIRED", ex.code)
    }

    @Test
    fun `approve flips the device to approved, only for the owning parent`() {
        val (parentId, childId) = newParentAndChild()
        val (otherParentId, _) = newParentAndChild()
        val code = pairingService.generateCode(parentId, childId)
        val claimed = pairingService.claim(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))
        val deviceId = UUID.fromString(claimed.deviceId)

        // Anti-IDOR: a different parent gets 404, never 403, and never flips the device.
        val ex = assertThrows(ApiException.NotFound::class.java) {
            pairingService.approve(otherParentId, deviceId)
        }
        assertEquals("DEVICE_NOT_FOUND", ex.code)

        val approved = pairingService.approve(parentId, deviceId)
        assertEquals(DeviceStatus.APPROVED, approved.status)

        val status = pairingService.pollStatus(deviceId, claimed.claimTicket)
        assertEquals(DeviceStatus.APPROVED, status.status)
    }

    @Test
    fun `token-exchange only succeeds after approval, and the ticket is single-use`() {
        val (parentId, childId) = newParentAndChild()
        val code = pairingService.generateCode(parentId, childId)
        val claimed = pairingService.claim(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))
        val deviceId = UUID.fromString(claimed.deviceId)

        // Not approved yet.
        assertThrows(ApiException.Conflict::class.java) {
            pairingService.tokenExchange(deviceId, claimed.claimTicket)
        }

        pairingService.approve(parentId, deviceId)
        val tokens = pairingService.tokenExchange(deviceId, claimed.claimTicket)
        assertNotEquals("", tokens.deviceAccessToken)
        assertNotEquals("", tokens.deviceRefreshToken)

        // Single-use: the same ticket cannot be redeemed twice.
        assertThrows(ApiException.Unauthorized::class.java) {
            pairingService.tokenExchange(deviceId, claimed.claimTicket)
        }
    }

    @Test
    fun `revoke sets status=revoked and reusing its refresh token is rejected`() {
        val (parentId, childId) = newParentAndChild()
        val code = pairingService.generateCode(parentId, childId)
        val claimed = pairingService.claim(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))
        val deviceId = UUID.fromString(claimed.deviceId)
        pairingService.approve(parentId, deviceId)
        val tokens = pairingService.tokenExchange(deviceId, claimed.claimTicket)

        pairingService.revoke(parentId, deviceId)

        assertThrows(ApiException.Unauthorized::class.java) {
            pairingService.refreshDeviceToken(DeviceTokenRefreshRequest(tokens.deviceRefreshToken))
        }
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
