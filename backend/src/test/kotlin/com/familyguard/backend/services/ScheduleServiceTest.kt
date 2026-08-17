package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.UpsertScheduleRequest
import com.familyguard.shared.enums.ScheduleMode
import com.familyguard.shared.enums.Weekday
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class ScheduleServiceTest {

    private val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
    private val authService = AuthService(jwtConfig)
    private val childService = ChildService()
    private val scheduleService = ScheduleService()

    private fun newParentAndChild(): Pair<UUID, UUID> {
        val signup = authService.signup(SignupRequest("schedule-test-${UUID.randomUUID()}@example.com", "password123", "Parent"))
        val parentId = UUID.fromString(signup.parentId)
        val child = childService.createChild(parentId, CreateChildRequest("Kid", 2015))
        return parentId to UUID.fromString(child.id)
    }

    @Test
    fun `creating a schedule stores days, times, and mode as given`() {
        val (parentId, childId) = newParentAndChild()

        val schedule = scheduleService.createSchedule(
            parentId,
            childId,
            UpsertScheduleRequest(
                name = "Bedtime",
                daysOfWeek = listOf(Weekday.MON, Weekday.TUE, Weekday.WED, Weekday.THU, Weekday.FRI),
                startTime = "21:00",
                endTime = "07:00",
                mode = ScheduleMode.BLOCK,
            ),
        )

        assertEquals("Bedtime", schedule.name)
        assertEquals(5, schedule.daysOfWeek.size)
        assertEquals("21:00", schedule.startTime)
        assertEquals("07:00", schedule.endTime)
        assertEquals(ScheduleMode.BLOCK, schedule.mode)
    }

    @Test
    fun `creating a schedule with no days is rejected`() {
        val (parentId, childId) = newParentAndChild()

        val ex = assertThrows(ApiException.BadRequest::class.java) {
            scheduleService.createSchedule(
                parentId,
                childId,
                UpsertScheduleRequest(name = "Empty", daysOfWeek = emptyList(), startTime = "09:00", endTime = "10:00", mode = ScheduleMode.BLOCK),
            )
        }
        assertEquals("INVALID_DAYS_OF_WEEK", ex.code)
    }

    @Test
    fun `creating a schedule with equal start and end time is rejected`() {
        val (parentId, childId) = newParentAndChild()

        val ex = assertThrows(ApiException.BadRequest::class.java) {
            scheduleService.createSchedule(
                parentId,
                childId,
                UpsertScheduleRequest(name = "Zero-length", daysOfWeek = listOf(Weekday.MON), startTime = "09:00", endTime = "09:00", mode = ScheduleMode.BLOCK),
            )
        }
        assertEquals("INVALID_TIME_RANGE", ex.code)
    }

    @Test
    fun `deleting a schedule removes it from the list`() {
        val (parentId, childId) = newParentAndChild()
        val schedule = scheduleService.createSchedule(
            parentId,
            childId,
            UpsertScheduleRequest(name = "Study", daysOfWeek = listOf(Weekday.SAT), startTime = "10:00", endTime = "12:00", mode = ScheduleMode.ALLOW_ONLY),
        )

        scheduleService.deleteSchedule(parentId, childId, UUID.fromString(schedule.id))

        assertTrue(scheduleService.listSchedules(childId).none { it.id == schedule.id })
    }

    @Test
    fun `deleting a schedule that doesn't exist throws NOT_FOUND`() {
        val (parentId, childId) = newParentAndChild()

        val ex = assertThrows(ApiException.NotFound::class.java) {
            scheduleService.deleteSchedule(parentId, childId, UUID.randomUUID())
        }
        assertEquals("SCHEDULE_NOT_FOUND", ex.code)
    }

    @Test
    fun `a non-owning parent gets NOT_FOUND, never a leak of the child's existence`() {
        val (_, childId) = newParentAndChild()
        val (otherParentId, _) = newParentAndChild()

        val ex = assertThrows(ApiException.NotFound::class.java) {
            scheduleService.createSchedule(
                otherParentId,
                childId,
                UpsertScheduleRequest(name = "x", daysOfWeek = listOf(Weekday.MON), startTime = "09:00", endTime = "10:00", mode = ScheduleMode.BLOCK),
            )
        }
        assertEquals("CHILD_NOT_FOUND", ex.code)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpDatabase() {
            DatabaseFactory.init(
                jdbcUrl = "jdbc:postgresql://localhost:5432/familyguard",
                user = "familyguard",
                password = "familyguard",
            )
        }
    }
}
