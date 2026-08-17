package com.familyguard.backend.plugins

import com.familyguard.backend.auth.CLAIM_CHILD_ID
import com.familyguard.backend.auth.CLAIM_ROLE
import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.auth.ROLE_DEVICE
import com.familyguard.backend.auth.ROLE_PARENT
import io.ktor.server.application.Application
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt

const val AUTH_PARENT = "auth-parent"
const val AUTH_DEVICE = "auth-device"

/**
 * Two independent JWT Authentication providers, both backed by the same HS256
 * verifier/secret, distinguished by the `role` claim (per api-spec.md's Auth model). Each
 * route picks exactly one via authenticate("auth-parent") { } or authenticate("auth-device") { }.
 */
fun Application.configureSecurity(jwtConfig: JwtConfig) {
    install(Authentication) {
        jwt(AUTH_PARENT) {
            verifier(jwtConfig.verifier)
            validate { credential ->
                val role = credential.payload.getClaim(CLAIM_ROLE).asString()
                if (role == ROLE_PARENT && credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
        jwt(AUTH_DEVICE) {
            verifier(jwtConfig.verifier)
            validate { credential ->
                val role = credential.payload.getClaim(CLAIM_ROLE).asString()
                val childId = credential.payload.getClaim(CLAIM_CHILD_ID).asString()
                if (role == ROLE_DEVICE && credential.payload.subject != null && !childId.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
