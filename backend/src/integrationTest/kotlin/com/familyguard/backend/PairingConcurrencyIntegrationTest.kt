package com.familyguard.backend

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.plugins.ApiException
import com.familyguard.backend.services.AuthService
import com.familyguard.backend.services.ChildService
import com.familyguard.backend.services.PairingService
import com.familyguard.shared.dto.ClaimRequest
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.SignupRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single most important test in this backend (per docs/testing-plan.md and
 * docs/pairing-security.md): fires genuinely simultaneous `claim()` calls at ONE pairing
 * code against a real Postgres and asserts that the atomic
 * `UPDATE ... WHERE status='pending' ... RETURNING` really only lets one caller win. A
 * naive SELECT-then-UPDATE would let multiple concurrent callers both observe
 * status='pending' and both "succeed" - this test would fail against that implementation.
 */
@Testcontainers
class PairingConcurrencyIntegrationTest {

    @Test
    fun `only one concurrent claim attempt succeeds, all others see CODE_ALREADY_CLAIMED`() {
        val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
        val authService = AuthService(jwtConfig)
        val childService = ChildService()
        val pairingService = PairingService(jwtConfig)

        val signup = authService.signup(SignupRequest("concurrency-${UUID.randomUUID()}@example.com", "password123", "Parent"))
        val parentId = UUID.fromString(signup.parentId)
        val child = childService.createChild(parentId, CreateChildRequest("Kid", 2015))
        val childId = UUID.fromString(child.id)
        val code = pairingService.generateCode(parentId, childId)

        val concurrency = 25
        val executor = Executors.newFixedThreadPool(concurrency)
        val readyLatch = CountDownLatch(concurrency)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(concurrency)

        val successes = AtomicInteger(0)
        val alreadyClaimedConflicts = AtomicInteger(0)
        val unexpectedFailures = AtomicInteger(0)

        repeat(concurrency) {
            executor.submit {
                readyLatch.countDown()
                startLatch.await()
                try {
                    pairingService.claim(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))
                    successes.incrementAndGet()
                } catch (e: ApiException.Conflict) {
                    if (e.code == "CODE_ALREADY_CLAIMED") {
                        alreadyClaimedConflicts.incrementAndGet()
                    } else {
                        unexpectedFailures.incrementAndGet()
                    }
                } catch (e: Exception) {
                    unexpectedFailures.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        // Release every thread at (as close as the JVM allows to) the same instant.
        readyLatch.await()
        startLatch.countDown()
        val finishedInTime = doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(true, finishedInTime, "all claim attempts should finish within 30s")
        assertEquals(0, unexpectedFailures.get(), "no attempt should fail for any reason other than the code already being claimed")
        assertEquals(1, successes.get(), "exactly one concurrent claim attempt must win the race")
        assertEquals(concurrency - 1, alreadyClaimedConflicts.get(), "every loser must see CODE_ALREADY_CLAIMED, not silently succeed")
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("familyguard")
            .withUsername("familyguard")
            .withPassword("familyguard")

        @JvmStatic
        @BeforeAll
        fun setUpDatabase() {
            DatabaseFactory.init(postgres.jdbcUrl, postgres.username, postgres.password)
        }
    }
}
