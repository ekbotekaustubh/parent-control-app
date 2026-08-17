package com.familyguard.backend

import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.ChildResponse
import com.familyguard.shared.dto.ClaimRequest
import com.familyguard.shared.dto.ClaimResponse
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.DashboardResponse
import com.familyguard.shared.dto.DeviceTokensResponse
import com.familyguard.shared.dto.PairingCodeResponse
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.SignupResponse
import com.familyguard.shared.dto.TokenExchangeRequest
import com.familyguard.shared.dto.UpsertRuleRequest
import com.familyguard.shared.dto.UsageEvent
import com.familyguard.shared.dto.UsageSyncRequest
import com.familyguard.shared.dto.UsageSyncResponse
import com.familyguard.shared.enums.RuleType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.util.UUID

/**
 * Full HTTP-layer walk through docs/architecture.md's "Usage reporting" data flow: set a
 * rule, sync usage as the paired device, and confirm the parent's dashboard reflects both
 * the rule and the synced usage in one read - including the over-limit flag.
 */
@Testcontainers
class UsageAndDashboardIntegrationTest {

    private data class PairedContext(val parentAuthHeader: String, val childId: String, val deviceAuthHeader: String)

    private suspend fun pairANewDevice(client: HttpClient): PairedContext {
        val email = "usage-dash-${UUID.randomUUID()}@example.com"
        val signup = client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_SIGNUP}") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest(email, "password123", "Parent"))
        }.body<SignupResponse>()
        val parentAuthHeader = "Bearer ${signup.accessToken}"

        val child = client.post("${ApiPaths.BASE}/${ApiPaths.CHILDREN}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, parentAuthHeader)
            setBody(CreateChildRequest("Kid", 2015))
        }.body<ChildResponse>()

        val code = client.post("${ApiPaths.BASE}/${ApiPaths.PAIRING_CODES.replace("{childId}", child.id)}") {
            header(HttpHeaders.Authorization, parentAuthHeader)
        }.body<PairingCodeResponse>()

        val claim = client.post("${ApiPaths.BASE}/${ApiPaths.PAIRING_CLAIM}") {
            contentType(ContentType.Application.Json)
            setBody(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))
        }.body<ClaimResponse>()

        client.post("${ApiPaths.BASE}/${ApiPaths.DEVICE_APPROVE.replace("{deviceId}", claim.deviceId)}") {
            header(HttpHeaders.Authorization, parentAuthHeader)
        }

        val tokens = client.post("${ApiPaths.BASE}/${ApiPaths.DEVICE_TOKEN_EXCHANGE.replace("{deviceId}", claim.deviceId)}") {
            contentType(ContentType.Application.Json)
            setBody(TokenExchangeRequest(claim.claimTicket))
        }.body<DeviceTokensResponse>()

        return PairedContext(parentAuthHeader, child.id, "Bearer ${tokens.deviceAccessToken}")
    }

    @Test
    fun `setting a rule then syncing usage is reflected on the dashboard, including over-limit`() = testApplication {
        application { module(MapApplicationConfig()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val ctx = pairANewDevice(client)
        val packageName = "com.google.android.youtube"
        val today = LocalDate.now().toString()

        val rulePath = "${ApiPaths.BASE}/${ApiPaths.CHILD_APP_RULE
            .replace("{childId}", ctx.childId)
            .replace("{packageName}", packageName)}"
        val ruleResponse = client.put(rulePath) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, ctx.parentAuthHeader)
            setBody(UpsertRuleRequest(RuleType.ALLOW, dailyLimitMinutes = 45))
        }
        assertEquals(HttpStatusCode.OK, ruleResponse.status)

        val syncResponse = client.post("${ApiPaths.BASE}/${ApiPaths.DEVICE_USAGE_SYNC}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, ctx.deviceAuthHeader)
            setBody(UsageSyncRequest(listOf(UsageEvent(packageName, today, 32))))
        }
        assertEquals(HttpStatusCode.OK, syncResponse.status)
        assertEquals(1, syncResponse.body<UsageSyncResponse>().acceptedCount)

        val dashboardPath = "${ApiPaths.BASE}/${ApiPaths.CHILD_DASHBOARD.replace("{childId}", ctx.childId)}"
        val dashboard = client.get(dashboardPath) {
            header(HttpHeaders.Authorization, ctx.parentAuthHeader)
        }.body<DashboardResponse>()

        assertTrue(dashboard.rules.any { it.packageName == packageName && it.dailyLimitMinutes == 45 })
        val usageItem = dashboard.usageToday.single { it.packageName == packageName }
        assertEquals(32, usageItem.durationMinutes)
        assertFalse(usageItem.isOverLimit)

        // Push past the limit - the dashboard's isOverLimit flag must flip.
        client.post("${ApiPaths.BASE}/${ApiPaths.DEVICE_USAGE_SYNC}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, ctx.deviceAuthHeader)
            setBody(UsageSyncRequest(listOf(UsageEvent(packageName, today, 50))))
        }
        val dashboardAfterOverLimit = client.get(dashboardPath) {
            header(HttpHeaders.Authorization, ctx.parentAuthHeader)
        }.body<DashboardResponse>()
        val overLimitItem = dashboardAfterOverLimit.usageToday.single { it.packageName == packageName }
        assertEquals(50, overLimitItem.durationMinutes)
        assertTrue(overLimitItem.isOverLimit)
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
