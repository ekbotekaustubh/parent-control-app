package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.auth.PasswordHasher
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.LoginRequest
import com.familyguard.shared.dto.LogoutRequest
import com.familyguard.shared.dto.RefreshRequest
import com.familyguard.shared.dto.SignupRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Exercises AuthService directly (no HTTP layer) against the local dev Postgres started by
 * `docker compose up -d db` (see docker-compose.yml / architecture.md's setup steps) - this
 * is what keeps it a "unit" test in the sense the build separates (`./gradlew test`, no
 * Testcontainers orchestration needed here). Full HTTP-layer flows against a hermetic,
 * auto-provisioned Postgres live in src/integrationTest.
 */
class AuthServiceTest {

    private val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
    private val authService = AuthService(jwtConfig)

    private fun uniqueEmail() = "auth-test-${UUID.randomUUID()}@example.com"

    @Test
    fun `password hashing round-trips and rejects wrong password`() {
        val hash = PasswordHasher.hash("correct horse battery staple")
        assertTrue(PasswordHasher.verify("correct horse battery staple", hash))
        assertFalse(PasswordHasher.verify("wrong password", hash))
    }

    @Test
    fun `parent JWT carries role=parent and the parent id as subject`() {
        val parentId = UUID.randomUUID()
        val token = jwtConfig.issueParentAccessToken(parentId)
        val decoded = jwtConfig.verifier.verify(token)
        assertEquals(parentId.toString(), decoded.subject)
        assertEquals("parent", decoded.getClaim("role").asString())
    }

    @Test
    fun `device JWT carries role=device, deviceId as subject, and childId claim`() {
        val deviceId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val token = jwtConfig.issueDeviceAccessToken(deviceId, childId)
        val decoded = jwtConfig.verifier.verify(token)
        assertEquals(deviceId.toString(), decoded.subject)
        assertEquals("device", decoded.getClaim("role").asString())
        assertEquals(childId.toString(), decoded.getClaim("childId").asString())
    }

    @Test
    fun `signup then login succeeds and returns usable tokens`() {
        val email = uniqueEmail()
        val signupResponse = authService.signup(SignupRequest(email, "s3cret-password", "Parent One"))
        assertNotEquals("", signupResponse.accessToken)
        assertNotEquals("", signupResponse.refreshToken)

        val loginResponse = authService.login(LoginRequest(email, "s3cret-password"))
        assertNotEquals("", loginResponse.accessToken)
    }

    @Test
    fun `login with wrong password is rejected with INVALID_CREDENTIALS, same as unknown email`() {
        val email = uniqueEmail()
        authService.signup(SignupRequest(email, "correct-password", "Parent Two"))

        val wrongPasswordEx = assertThrows(ApiException.Unauthorized::class.java) {
            authService.login(LoginRequest(email, "wrong-password"))
        }
        assertEquals("INVALID_CREDENTIALS", wrongPasswordEx.code)

        val unknownEmailEx = assertThrows(ApiException.Unauthorized::class.java) {
            authService.login(LoginRequest("does-not-exist-${UUID.randomUUID()}@example.com", "whatever"))
        }
        // Same error code for both cases - never leak which one it was.
        assertEquals("INVALID_CREDENTIALS", unknownEmailEx.code)
    }

    @Test
    fun `refresh rotates the token - the old token can no longer be used`() {
        val email = uniqueEmail()
        val signup = authService.signup(SignupRequest(email, "password123", "Parent Three"))

        val rotated = authService.refresh(RefreshRequest(signup.refreshToken))
        assertNotEquals(signup.refreshToken, rotated.refreshToken)

        // The old (now-rotated-away) token must be rejected.
        assertThrows(ApiException.Unauthorized::class.java) {
            authService.refresh(RefreshRequest(signup.refreshToken))
        }
    }

    @Test
    fun `replaying an already-rotated refresh token revokes the entire chain`() {
        val email = uniqueEmail()
        val signup = authService.signup(SignupRequest(email, "password123", "Parent Four"))

        val rotated = authService.refresh(RefreshRequest(signup.refreshToken))

        // Reuse of the original (already-rotated) token - detected as compromise.
        val reuseEx = assertThrows(ApiException.Unauthorized::class.java) {
            authService.refresh(RefreshRequest(signup.refreshToken))
        }
        assertEquals("REFRESH_TOKEN_REUSED", reuseEx.code)

        // The entire chain is revoked, including the token that was legitimately issued by
        // the rotation above - it must be unusable now too.
        assertThrows(ApiException.Unauthorized::class.java) {
            authService.refresh(RefreshRequest(rotated.refreshToken))
        }
    }

    @Test
    fun `logout revokes the token so it can never be used to refresh again`() {
        val email = uniqueEmail()
        val signup = authService.signup(SignupRequest(email, "password123", "Parent Five"))

        authService.logout(LogoutRequest(signup.refreshToken))

        assertThrows(ApiException.Unauthorized::class.java) {
            authService.refresh(RefreshRequest(signup.refreshToken))
        }
    }

    @Test
    fun `logout is idempotent and never throws, even for an unknown token`() {
        authService.logout(LogoutRequest("not-a-real-token"))
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
