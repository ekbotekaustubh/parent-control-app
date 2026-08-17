package com.familyguard.backend.routes

import com.familyguard.backend.auth.CLAIM_ROLE
import com.familyguard.backend.auth.ROLE_DEVICE
import com.familyguard.backend.auth.requireDeviceContext
import com.familyguard.backend.auth.requireParentId
import com.familyguard.backend.plugins.AUTH_DEVICE
import com.familyguard.backend.plugins.AUTH_PARENT
import com.familyguard.backend.plugins.ApiException
import com.familyguard.backend.plugins.pathParam
import com.familyguard.backend.plugins.pathUuid
import com.familyguard.backend.services.RuleService
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.UpsertRuleRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.ruleRoutes(ruleService: RuleService) {
    authenticate(AUTH_PARENT) {
        put(ApiPaths.CHILD_APP_RULE) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            val packageName = call.pathParam("packageName")
            val request = call.receive<UpsertRuleRequest>()
            call.respond(ruleService.upsertRule(parentId, childId, packageName, request))
        }

        delete(ApiPaths.CHILD_APP_RULE) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            val packageName = call.pathParam("packageName")
            ruleService.deleteRule(parentId, childId, packageName)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    // Callable by the owning parent, or by a device whose JWT childId claim matches
    // (api-spec.md). Ktor's authenticate() with multiple provider names accepts either.
    authenticate(AUTH_PARENT, AUTH_DEVICE) {
        get(ApiPaths.CHILD_RULES) {
            val childId = call.pathUuid("childId")
            val principal = call.principal<JWTPrincipal>()
            val role = principal?.payload?.getClaim(CLAIM_ROLE)?.asString()

            if (role == ROLE_DEVICE) {
                val deviceContext = call.requireDeviceContext()
                if (deviceContext.childId != childId) {
                    throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")
                }
            } else {
                val parentId = call.requireParentId()
                if (!ruleService.isOwnedChild(parentId, childId)) {
                    throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")
                }
            }

            call.respond(ruleService.listRules(childId))
        }
    }
}
