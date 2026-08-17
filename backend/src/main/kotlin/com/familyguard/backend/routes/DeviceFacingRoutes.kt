package com.familyguard.backend.routes

import com.familyguard.backend.auth.requireDeviceContext
import com.familyguard.backend.plugins.AUTH_DEVICE
import com.familyguard.backend.services.AccessRequestService
import com.familyguard.backend.services.ChildService
import com.familyguard.backend.services.RuleService
import com.familyguard.backend.services.UsageService
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.DeviceConfigResponse
import com.familyguard.shared.dto.UsageSyncRequest
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Device-authenticated only. deviceId/childId are ALWAYS taken from the verified JWT
 * (requireDeviceContext()), never from any client-supplied body/path value - there is none
 * to take them from here, which is itself the anti-IDOR guarantee for this group of routes.
 */
fun Route.deviceFacingRoutes(
    usageService: UsageService,
    ruleService: RuleService,
    childService: ChildService,
    accessRequestService: AccessRequestService,
) {
    authenticate(AUTH_DEVICE) {
        get(ApiPaths.DEVICE_CONFIG) {
            val ctx = call.requireDeviceContext()
            val rules = ruleService.listRules(ctx.childId)
            val overrides = accessRequestService.activeOverridesFor(ctx.childId)
            val configVersion = childService.getConfigVersion(ctx.childId)
            call.respond(
                DeviceConfigResponse(
                    childId = ctx.childId.toString(),
                    rules = rules,
                    overrides = overrides,
                    configVersion = configVersion,
                    syncIntervalSeconds = 900L,
                    serverTimeUtc = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                ),
            )
        }

        post(ApiPaths.DEVICE_USAGE_SYNC) {
            val ctx = call.requireDeviceContext()
            val request = call.receive<UsageSyncRequest>()
            call.respond(usageService.syncUsage(ctx.deviceId, ctx.childId, request.events))
        }

        post(ApiPaths.DEVICE_HEARTBEAT) {
            val ctx = call.requireDeviceContext()
            call.respond(usageService.heartbeat(ctx.deviceId, ctx.childId))
        }
    }
}
