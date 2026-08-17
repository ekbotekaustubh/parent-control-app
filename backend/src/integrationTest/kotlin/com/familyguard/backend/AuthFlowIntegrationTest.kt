package com.familyguard.backend

import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.AuthTokensResponse
import com.familyguard.shared.dto.LoginRequest
import com.familyguard.shared.dto.LogoutRequest
import com.familyguard.shared.dto.RefreshRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.SignupResponse
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Full HTTP-layer coverage of api-spec.md's "Auth (parent)" section: signup -> login ->
 * refresh -> logout, plus proof a freshly-issued access token is actually usable on a
 * protected route, and that a revoked (logged-out) refresh token can't be reused.
 */
@Testcontainers
class AuthFlowIntegrationTest {

    @Test
    fun `signup, login, refresh, and logout all work end-to-end over HTTP`() = testApplication {
        application { module(MapApplicationConfig()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val email = "flow-${UUID.randomUUID()}@example.com"

        val signupResponse = client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_SIGNUP}") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest(email, "password123", "Parent One"))
        }
        assertEquals(HttpStatusCode.Created, signupResponse.status)
        val signup = signupResponse.body<SignupResponse>()

        val loginResponse = client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_LOGIN}") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, "password123"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val login = loginResponse.body<AuthTokensResponse>()

        val refreshResponse = client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_REFRESH}") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(login.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshResponse.status)
        val refreshed = refreshResponse.body<AuthTokensResponse>()
        assertNotEquals(login.refreshToken, refreshed.refreshToken)

        // Prove the fresh access token is real and usable on a protected route.
        val listChildrenResponse = client.get("${ApiPaths.BASE}/${ApiPaths.CHILDREN}") {
            header(HttpHeaders.Authorization, "Bearer ${refreshed.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, listChildrenResponse.status)

        // The already-superseded login-issued refresh token must be rejected (rotation).
        val staleRefreshResponse = client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_REFRESH}") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(login.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, staleRefreshResponse.status)

        val logoutResponse = client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_LOGOUT}") {
            contentType(ContentType.Application.Json)
            setBody(LogoutRequest(refreshed.refreshToken))
        }
        assertEquals(HttpStatusCode.NoContent, logoutResponse.status)

        val reuseAfterLogoutResponse = client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_REFRESH}") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshed.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, reuseAfterLogoutResponse.status)
    }

    @Test
    fun `login with a wrong password returns 401 INVALID_CREDENTIALS`() = testApplication {
        application { module(MapApplicationConfig()) }
        val client = createClient { install(ContentNegotiation) { json() } }

        val email = "badlogin-${UUID.randomUUID()}@example.com"
        client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_SIGNUP}") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest(email, "correct-password", "Parent"))
        }

        val response = client.post("${ApiPaths.BASE}/${ApiPaths.AUTH_LOGIN}") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, "wrong-password"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
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
