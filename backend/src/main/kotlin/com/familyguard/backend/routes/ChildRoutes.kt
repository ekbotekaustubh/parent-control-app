package com.familyguard.backend.routes

import com.familyguard.backend.auth.requireParentId
import com.familyguard.backend.plugins.AUTH_PARENT
import com.familyguard.backend.plugins.pathUuid
import com.familyguard.backend.services.ChildService
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.CreateChildRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.childRoutes(childService: ChildService) {
    authenticate(AUTH_PARENT) {
        post(ApiPaths.CHILDREN) {
            val parentId = call.requireParentId()
            val request = call.receive<CreateChildRequest>()
            call.respond(HttpStatusCode.Created, childService.createChild(parentId, request))
        }

        get(ApiPaths.CHILDREN) {
            val parentId = call.requireParentId()
            call.respond(childService.listChildren(parentId))
        }

        // 404 for both "doesn't exist" and "exists but isn't yours" - never 403 (api-spec.md).
        get(ApiPaths.CHILD_BY_ID) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            call.respond(childService.getOwnedChild(parentId, childId))
        }
    }
}
