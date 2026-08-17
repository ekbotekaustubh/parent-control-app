package com.familyguard.backend.services

import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.db.tables.ScheduleModeValues
import com.familyguard.backend.db.tables.SchedulesTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.ScheduleResponse
import com.familyguard.shared.dto.UpsertScheduleRequest
import com.familyguard.shared.enums.ScheduleMode
import com.familyguard.shared.enums.Weekday
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * "Schedules" (docs/roadmap.md) - blanket time windows (bedtime/school-hours). Create/list/
 * delete only in this pass, no update endpoint - deleting and recreating covers edits for
 * this slice's scope, same minimal-CRUD depth as pairing codes (generate/claim/revoke, no
 * "edit a pairing code").
 */
class ScheduleService {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun createSchedule(parentId: UUID, childId: UUID, request: UpsertScheduleRequest): ScheduleResponse = transaction {
        requireOwnedChild(parentId, childId)
        if (request.daysOfWeek.isEmpty()) {
            throw ApiException.BadRequest("INVALID_DAYS_OF_WEEK", "Select at least one day.")
        }
        val startTime = parseTime(request.startTime)
        val endTime = parseTime(request.endTime)
        if (startTime == endTime) {
            throw ApiException.BadRequest("INVALID_TIME_RANGE", "Start and end time can't be the same.")
        }

        val id = SchedulesTable.insertAndGetId {
            it[SchedulesTable.childId] = childId
            it[name] = request.name
            it[daysOfWeek] = request.daysOfWeek.joinToString(",") { day -> day.name }
            it[SchedulesTable.startTime] = startTime
            it[SchedulesTable.endTime] = endTime
            it[mode] = if (request.mode == ScheduleMode.ALLOW_ONLY) ScheduleModeValues.ALLOW_ONLY else ScheduleModeValues.BLOCK
        }.value

        SchedulesTable.selectAll().where { SchedulesTable.id eq id }.single().toResponse()
    }

    /** Callable by the owning parent OR a device whose JWT childId claim matches - same split as RuleService.listRules. */
    fun listSchedules(childId: UUID): List<ScheduleResponse> = transaction {
        SchedulesTable.selectAll()
            .where { (SchedulesTable.childId eq childId) and (SchedulesTable.isActive eq true) }
            .map { it.toResponse() }
    }

    fun deleteSchedule(parentId: UUID, childId: UUID, scheduleId: UUID) {
        transaction {
            requireOwnedChild(parentId, childId)
            val deleted = SchedulesTable.deleteWhere { (SchedulesTable.id eq scheduleId) and (SchedulesTable.childId eq childId) }
            if (deleted == 0) {
                throw ApiException.NotFound("SCHEDULE_NOT_FOUND", "No schedule with that id.")
            }
            // app_rules.schedule_id referencing this row is detached automatically
            // (ON DELETE SET NULL, V15__add_schedule_id_to_app_rules.sql) - no extra cleanup here.
        }
    }

    fun isOwnedChild(parentId: UUID, childId: UUID): Boolean = transaction {
        ChildrenTable.selectAll()
            .where { (ChildrenTable.id eq childId) and (ChildrenTable.parentId eq parentId) }
            .count() > 0
    }

    private fun requireOwnedChild(parentId: UUID, childId: UUID) {
        if (!isOwnedChild(parentId, childId)) {
            throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")
        }
    }

    private fun parseTime(value: String): LocalTime =
        try {
            LocalTime.parse(value, timeFormatter)
        } catch (e: Exception) {
            throw ApiException.BadRequest("INVALID_TIME", "Time must be in HH:mm format.")
        }

    private fun ResultRow.toResponse(): ScheduleResponse = ScheduleResponse(
        id = this[SchedulesTable.id].value.toString(),
        childId = this[SchedulesTable.childId].value.toString(),
        name = this[SchedulesTable.name],
        daysOfWeek = this[SchedulesTable.daysOfWeek].split(",").filter { it.isNotBlank() }.map { Weekday.valueOf(it) },
        startTime = this[SchedulesTable.startTime].format(timeFormatter),
        endTime = this[SchedulesTable.endTime].format(timeFormatter),
        mode = if (this[SchedulesTable.mode] == ScheduleModeValues.ALLOW_ONLY) ScheduleMode.ALLOW_ONLY else ScheduleMode.BLOCK,
        isActive = this[SchedulesTable.isActive],
    )
}
