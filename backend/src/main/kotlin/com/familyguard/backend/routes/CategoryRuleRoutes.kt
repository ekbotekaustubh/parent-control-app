package com.familyguard.backend.routes

import com.familyguard.backend.auth.requireParentId
import com.familyguard.backend.plugins.ApiException
import com.familyguard.backend.plugins.AUTH_PARENT
import com.familyguard.backend.plugins.pathParam
import com.familyguard.backend.plugins.pathUuid
import com.familyguard.backend.services.CategoryRuleService
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.UpsertCategoryRuleRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put

fun Route.categoryRuleRoutes(categoryRuleService: CategoryRuleService) {
    authenticate(AUTH_PARENT) {
        put(ApiPaths.CHILD_CATEGORY_RULE) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            val category = call.pathParam("category")
            val request = call.receive<UpsertCategoryRuleRequest>()
            call.respond(categoryRuleService.upsertCategoryRule(parentId, childId, category, request))
        }

        get(ApiPaths.CHILD_CATEGORY_RULES) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            if (!categoryRuleService.isOwnedChild(parentId, childId)) {
                throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")
            }
            call.respond(categoryRuleService.listCategoryRules(childId))
        }

        delete(ApiPaths.CHILD_CATEGORY_RULE) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            val category = call.pathParam("category")
            categoryRuleService.deleteCategoryRule(parentId, childId, category)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
