package com.familyguard.backend.routes

import com.familyguard.backend.auth.requireParentId
import com.familyguard.backend.plugins.AUTH_PARENT
import com.familyguard.backend.plugins.ApiException
import com.familyguard.backend.plugins.pathUuid
import com.familyguard.backend.services.PairingService
import com.familyguard.shared.ApiPaths
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.util.UUID

fun Route.deviceRoutes(pairingService: PairingService) {
    authenticate(AUTH_PARENT) {
        // This is the *only* path that can flip a device to 'approved' (pairing-security.md).
        post(ApiPaths.DEVICE_APPROVE) {
            val parentId = call.requireParentId()
            val deviceId = call.pathUuid("deviceId")
            call.respond(pairingService.approve(parentId, deviceId))
        }

        get(ApiPaths.DEVICES) {
            val parentId = call.requireParentId()
            val childIdRaw = call.request.queryParameters["childId"]
                ?: throw ApiException.BadRequest("MISSING_PARAM", "childId query parameter is required.")
            val childId = try {
                UUID.fromString(childIdRaw)
            } catch (e: IllegalArgumentException) {
                throw ApiException.BadRequest("INVALID_PARAM", "Invalid childId.")
            }
            call.respond(pairingService.listDevices(parentId, childId))
        }

        post(ApiPaths.DEVICE_REVOKE) {
            val parentId = call.requireParentId()
            val deviceId = call.pathUuid("deviceId")
            pairingService.revoke(parentId, deviceId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
