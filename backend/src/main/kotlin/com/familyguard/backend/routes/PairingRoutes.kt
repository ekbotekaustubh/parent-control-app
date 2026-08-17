package com.familyguard.backend.routes

import com.familyguard.backend.auth.requireParentId
import com.familyguard.backend.plugins.AUTH_PARENT
import com.familyguard.backend.plugins.ApiException
import com.familyguard.backend.plugins.PAIRING_CLAIM_RATE_LIMIT
import com.familyguard.backend.plugins.pathUuid
import com.familyguard.backend.services.PairingService
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.ClaimRequest
import com.familyguard.shared.dto.DeviceTokenRefreshRequest
import com.familyguard.shared.dto.TokenExchangeRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Unauthenticated by design (per pairing-security.md): a device claiming a pairing code has
 * no credentials yet. Protection is the code's short TTL, single-use atomic claim, hashing
 * at rest, and IP rate limiting on /pairing/claim specifically (api-spec.md).
 */
fun Route.pairingRoutes(pairingService: PairingService) {
    authenticate(AUTH_PARENT) {
        post(ApiPaths.PAIRING_CODES) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            call.respond(HttpStatusCode.Created, pairingService.generateCode(parentId, childId))
        }
    }

    rateLimit(PAIRING_CLAIM_RATE_LIMIT) {
        post(ApiPaths.PAIRING_CLAIM) {
            val request = call.receive<ClaimRequest>()
            call.respond(pairingService.claim(request))
        }
    }

    get(ApiPaths.PAIRING_CLAIM_STATUS) {
        val deviceId = call.pathUuid("deviceId")
        val claimTicket = call.request.header(ApiPaths.CLAIM_TICKET_HEADER)
            ?: throw ApiException.Unauthorized("MISSING_CLAIM_TICKET", "X-Claim-Ticket header is required.")
        call.respond(pairingService.pollStatus(deviceId, claimTicket))
    }

    post(ApiPaths.DEVICE_TOKEN_EXCHANGE) {
        val deviceId = call.pathUuid("deviceId")
        val request = call.receive<TokenExchangeRequest>()
        call.respond(pairingService.tokenExchange(deviceId, request.claimTicket))
    }

    post(ApiPaths.DEVICE_TOKEN_REFRESH) {
        val request = call.receive<DeviceTokenRefreshRequest>()
        call.respond(pairingService.refreshDeviceToken(request))
    }
}
