package com.familyguard.backend.plugins

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.routes.accessRequestRoutes
import com.familyguard.backend.routes.authRoutes
import com.familyguard.backend.routes.categoryRuleRoutes
import com.familyguard.backend.routes.childRoutes
import com.familyguard.backend.routes.dashboardRoutes
import com.familyguard.backend.routes.deviceFacingRoutes
import com.familyguard.backend.routes.deviceRoutes
import com.familyguard.backend.routes.pairingRoutes
import com.familyguard.backend.routes.reportRoutes
import com.familyguard.backend.routes.ruleRoutes
import com.familyguard.backend.routes.scheduleRoutes
import com.familyguard.backend.services.AccessRequestService
import com.familyguard.backend.services.AuthService
import com.familyguard.backend.services.CategoryRuleService
import com.familyguard.backend.services.ChildService
import com.familyguard.backend.services.DashboardService
import com.familyguard.backend.services.InsightsService
import com.familyguard.backend.services.PairingService
import com.familyguard.backend.services.ReportService
import com.familyguard.backend.services.RuleService
import com.familyguard.backend.services.ScheduleService
import com.familyguard.backend.services.UsageService
import com.familyguard.shared.ApiPaths
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.UUID

fun Application.configureRouting(jwtConfig: JwtConfig) {
    val authService = AuthService(jwtConfig)
    val childService = ChildService()
    val pairingService = PairingService(jwtConfig)
    val ruleService = RuleService()
    val usageService = UsageService()
    val dashboardService = DashboardService(ruleService)
    val accessRequestService = AccessRequestService()
    val categoryRuleService = CategoryRuleService()
    val scheduleService = ScheduleService()
    val reportService = ReportService()
    val insightsService = InsightsService()

    routing {
        route(ApiPaths.BASE) {
            authRoutes(authService)
            childRoutes(childService)
            pairingRoutes(pairingService)
            deviceRoutes(pairingService)
            ruleRoutes(ruleService)
            deviceFacingRoutes(usageService, ruleService, childService, accessRequestService, categoryRuleService, scheduleService)
            accessRequestRoutes(accessRequestService)
            categoryRuleRoutes(categoryRuleService)
            scheduleRoutes(scheduleService)
            reportRoutes(reportService, insightsService)
            dashboardRoutes(dashboardService)
        }
    }
}

/** Parses a UUID path parameter, or throws a uniform 400 if it's missing/malformed. */
fun ApplicationCall.pathUuid(name: String): UUID {
    val raw = parameters[name] ?: throw ApiException.BadRequest("MISSING_PARAM", "Missing path parameter: $name")
    return try {
        UUID.fromString(raw)
    } catch (e: IllegalArgumentException) {
        throw ApiException.BadRequest("INVALID_PARAM", "Invalid id: $name")
    }
}

/** Non-UUID path parameter (e.g. an Android package name), required. */
fun ApplicationCall.pathParam(name: String): String =
    parameters[name] ?: throw ApiException.BadRequest("MISSING_PARAM", "Missing path parameter: $name")
