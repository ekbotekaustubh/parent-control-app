package com.familyguard.backend.routes

import com.familyguard.backend.auth.requireParentId
import com.familyguard.backend.plugins.AUTH_PARENT
import com.familyguard.backend.plugins.pathUuid
import com.familyguard.backend.services.DashboardService
import com.familyguard.shared.ApiPaths
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.dashboardRoutes(dashboardService: DashboardService) {
    authenticate(AUTH_PARENT) {
        get(ApiPaths.CHILD_DASHBOARD) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            call.respond(dashboardService.getDashboard(parentId, childId))
        }
    }
}
