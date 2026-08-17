package com.familyguard.backend.routes

import com.familyguard.backend.auth.requireParentId
import com.familyguard.backend.plugins.ApiException
import com.familyguard.backend.plugins.AUTH_PARENT
import com.familyguard.backend.plugins.pathUuid
import com.familyguard.backend.services.InsightsService
import com.familyguard.backend.services.ReportService
import com.familyguard.shared.ApiPaths
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.LocalDate

fun Route.reportRoutes(reportService: ReportService, insightsService: InsightsService) {
    authenticate(AUTH_PARENT) {
        get(ApiPaths.CHILD_REPORT) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            val range = call.request.queryParameters["range"] ?: "weekly"
            val anchor = call.request.queryParameters["anchor"]?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: Exception) {
                    throw ApiException.BadRequest("INVALID_ANCHOR", "anchor must be an ISO date (YYYY-MM-DD).")
                }
            } ?: LocalDate.now()
            call.respond(reportService.getReport(parentId, childId, range, anchor))
        }

        get(ApiPaths.CHILD_INSIGHTS) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            call.respond(insightsService.getInsights(parentId, childId))
        }
    }
}
