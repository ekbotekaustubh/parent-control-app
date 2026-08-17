package com.familyguard.backend.routes

import com.familyguard.backend.plugins.AUTH_RATE_LIMIT
import com.familyguard.backend.services.AuthService
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.LoginRequest
import com.familyguard.shared.dto.LogoutRequest
import com.familyguard.shared.dto.RefreshRequest
import com.familyguard.shared.dto.SignupRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.authRoutes(authService: AuthService) {
    rateLimit(AUTH_RATE_LIMIT) {
        post(ApiPaths.AUTH_SIGNUP) {
            val request = call.receive<SignupRequest>()
            call.respond(HttpStatusCode.Created, authService.signup(request))
        }

        post(ApiPaths.AUTH_LOGIN) {
            val request = call.receive<LoginRequest>()
            call.respond(authService.login(request))
        }

        post(ApiPaths.AUTH_REFRESH) {
            val request = call.receive<RefreshRequest>()
            call.respond(authService.refresh(request))
        }

        post(ApiPaths.AUTH_LOGOUT) {
            val request = call.receive<LogoutRequest>()
            authService.logout(request)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
