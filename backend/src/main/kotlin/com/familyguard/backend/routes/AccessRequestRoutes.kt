package com.familyguard.backend.routes

import com.familyguard.backend.auth.requireDeviceContext
import com.familyguard.backend.auth.requireParentId
import com.familyguard.backend.plugins.AUTH_DEVICE
import com.familyguard.backend.plugins.AUTH_PARENT
import com.familyguard.backend.plugins.pathUuid
import com.familyguard.backend.services.AccessRequestService
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.CreateAccessRequestRequest
import com.familyguard.shared.dto.ResolveAccessRequestRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.accessRequestRoutes(accessRequestService: AccessRequestService) {
    authenticate(AUTH_DEVICE) {
        post(ApiPaths.DEVICE_ACCESS_REQUESTS) {
            val ctx = call.requireDeviceContext()
            val request = call.receive<CreateAccessRequestRequest>()
            call.respond(HttpStatusCode.Created, accessRequestService.createRequest(ctx.childId, request))
        }
    }

    authenticate(AUTH_PARENT) {
        get(ApiPaths.ACCESS_REQUESTS) {
            val parentId = call.requireParentId()
            call.respond(accessRequestService.listPendingForParent(parentId))
        }

        post(ApiPaths.ACCESS_REQUEST_RESOLVE) {
            val parentId = call.requireParentId()
            val requestId = call.pathUuid("requestId")
            val request = call.receive<ResolveAccessRequestRequest>()
            call.respond(accessRequestService.resolve(parentId, requestId, request))
        }
    }
}
