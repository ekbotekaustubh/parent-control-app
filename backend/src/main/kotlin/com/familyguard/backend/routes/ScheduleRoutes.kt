package com.familyguard.backend.routes

import com.familyguard.backend.auth.requireParentId
import com.familyguard.backend.plugins.ApiException
import com.familyguard.backend.plugins.AUTH_PARENT
import com.familyguard.backend.plugins.pathUuid
import com.familyguard.backend.services.ScheduleService
import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.UpsertScheduleRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.scheduleRoutes(scheduleService: ScheduleService) {
    authenticate(AUTH_PARENT) {
        post(ApiPaths.CHILD_SCHEDULES) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            val request = call.receive<UpsertScheduleRequest>()
            call.respond(HttpStatusCode.Created, scheduleService.createSchedule(parentId, childId, request))
        }

        get(ApiPaths.CHILD_SCHEDULES) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            if (!scheduleService.isOwnedChild(parentId, childId)) {
                throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")
            }
            call.respond(scheduleService.listSchedules(childId))
        }

        delete(ApiPaths.CHILD_SCHEDULE_BY_ID) {
            val parentId = call.requireParentId()
            val childId = call.pathUuid("childId")
            val scheduleId = call.pathUuid("scheduleId")
            scheduleService.deleteSchedule(parentId, childId, scheduleId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
