package com.familyguard.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.Date
import java.util.UUID

const val ROLE_PARENT = "parent"
const val ROLE_DEVICE = "device"
const val CLAIM_ROLE = "role"
const val CLAIM_CHILD_ID = "childId"

/**
 * Issues and verifies both parent and device JWTs from a single HS256 secret, distinguished
 * by the `role` claim (per api-spec.md's "Auth model" section). Both `auth-parent` and
 * `auth-device` Authentication providers (plugins/Security.kt) share this one verifier and
 * gate on the `role` claim in their `validate {}` blocks.
 */
class JwtConfig(
    secret: String,
    val issuer: String,
    val accessTokenTtlSeconds: Long,
) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    fun issueParentAccessToken(parentId: UUID): String =
        JWT.create()
            .withIssuer(issuer)
            .withSubject(parentId.toString())
            .withClaim(CLAIM_ROLE, ROLE_PARENT)
            .withIssuedAt(Date())
            .withExpiresAt(expiresAt())
            .sign(algorithm)

    fun issueDeviceAccessToken(deviceId: UUID, childId: UUID): String =
        JWT.create()
            .withIssuer(issuer)
            .withSubject(deviceId.toString())
            .withClaim(CLAIM_ROLE, ROLE_DEVICE)
            .withClaim(CLAIM_CHILD_ID, childId.toString())
            .withIssuedAt(Date())
            .withExpiresAt(expiresAt())
            .sign(algorithm)

    private fun expiresAt(): Date = Date(System.currentTimeMillis() + accessTokenTtlSeconds * 1000)
}

/** Parent id re-derived from the verified `auth-parent` JWT's `sub` claim. Never trust a path param. */
fun ApplicationCall.requireParentId(): UUID {
    val principal = principal<JWTPrincipal>() ?: error("auth-parent route missing JWTPrincipal")
    return UUID.fromString(principal.payload.subject)
}

/** Device id + childId re-derived from the verified `auth-device` JWT. Never trust a path param. */
fun ApplicationCall.requireDeviceContext(): DeviceContext {
    val principal = principal<JWTPrincipal>() ?: error("auth-device route missing JWTPrincipal")
    val deviceId = UUID.fromString(principal.payload.subject)
    val childId = UUID.fromString(principal.payload.getClaim(CLAIM_CHILD_ID).asString())
    return DeviceContext(deviceId, childId)
}

data class DeviceContext(val deviceId: UUID, val childId: UUID)
