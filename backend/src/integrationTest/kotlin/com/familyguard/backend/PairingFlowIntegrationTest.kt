package com.familyguard.backend

import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.ChildResponse
import com.familyguard.shared.dto.ClaimRequest
import com.familyguard.shared.dto.ClaimResponse
import com.familyguard.shared.dto.ClaimStatusResponse
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.DeviceConfigResponse
import com.familyguard.shared.dto.DeviceResponse
import com.familyguard.shared.dto.DeviceTokensResponse
import com.familyguard.shared.dto.PairingCodeResponse
import com.familyguard.shared.dto.SignupResponse
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.TokenExchangeRequest
import com.familyguard.shared.enums.DeviceStatus
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Full HTTP-layer walk through docs/pairing-security.md's flow: create a child, generate a
 * pairing code, claim it (unauthenticated), poll status, approve as the parent, exchange the
 * claim ticket for real device tokens, then fetch device config with them.
 */
@Testcontainers
class PairingFlowIntegrationTest {

    @Test
    fun `full pairing flow - child, code, claim, approve, token-exchange, config`() = testApplication {
        application { module(MapApplicationConfig()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val email = "pairing-flow-${UUID.randomUUID()}@example.com"
        val signup = client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_SIGNUP}") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest(email, "password123", "Parent"))
        }.body<SignupResponse>()
        val authHeader = "Bearer ${signup.accessToken}"

        val child = client.post("${ApiPaths.BASE}/${ApiPaths.CHILDREN}") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, authHeader)
            setBody(CreateChildRequest("Kid", 2015))
        }.body<ChildResponse>()

        val pairingCodesPath = "${ApiPaths.BASE}/${ApiPaths.PAIRING_CODES.replace("{childId}", child.id)}"
        val codeResponse = client.post(pairingCodesPath) {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.Created, codeResponse.status)
        val code = codeResponse.body<PairingCodeResponse>()

        // Unauthenticated claim - the whole point of this endpoint.
        val claimResponse = client.post("${ApiPaths.BASE}/${ApiPaths.PAIRING_CLAIM}") {
            contentType(ContentType.Application.Json)
            setBody(ClaimRequest(code.code, "Pixel 8", "14", "1.0.0"))
        }
        assertEquals(HttpStatusCode.OK, claimResponse.status)
        val claim = claimResponse.body<ClaimResponse>()

        val statusPath = "${ApiPaths.BASE}/${ApiPaths.PAIRING_CLAIM_STATUS.replace("{deviceId}", claim.deviceId)}"
        val pendingStatus = client.get(statusPath) {
            header(ApiPaths.CLAIM_TICKET_HEADER, claim.claimTicket)
        }.body<ClaimStatusResponse>()
        assertEquals(DeviceStatus.PENDING_APPROVAL, pendingStatus.status)

        val approvePath = "${ApiPaths.BASE}/${ApiPaths.DEVICE_APPROVE.replace("{deviceId}", claim.deviceId)}"
        val approveResponse = client.post(approvePath) {
            header(HttpHeaders.Authorization, authHeader)
        }
        assertEquals(HttpStatusCode.OK, approveResponse.status)
        assertEquals(DeviceStatus.APPROVED, approveResponse.body<DeviceResponse>().status)

        val exchangePath = "${ApiPaths.BASE}/${ApiPaths.DEVICE_TOKEN_EXCHANGE.replace("{deviceId}", claim.deviceId)}"
        val exchangeResponse = client.post(exchangePath) {
            contentType(ContentType.Application.Json)
            setBody(TokenExchangeRequest(claim.claimTicket))
        }
        assertEquals(HttpStatusCode.OK, exchangeResponse.status)
        val deviceTokens = exchangeResponse.body<DeviceTokensResponse>()
        assertTrue(deviceTokens.deviceAccessToken.isNotBlank())

        val configResponse = client.get("${ApiPaths.BASE}/${ApiPaths.DEVICE_CONFIG}") {
            header(HttpHeaders.Authorization, "Bearer ${deviceTokens.deviceAccessToken}")
        }
        assertEquals(HttpStatusCode.OK, configResponse.status)
        val config = configResponse.body<DeviceConfigResponse>()
        assertEquals(child.id, config.childId)
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
