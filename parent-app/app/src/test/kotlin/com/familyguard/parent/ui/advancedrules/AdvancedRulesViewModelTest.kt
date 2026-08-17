package com.familyguard.parent.ui.advancedrules

import com.familyguard.parent.MainDispatcherRule
import com.familyguard.parent.data.remote.ApiException
import com.familyguard.parent.data.repository.CategoryRuleRepository
import com.familyguard.parent.data.repository.ScheduleRepository
import com.familyguard.shared.dto.CategoryRuleResponse
import com.familyguard.shared.dto.ScheduleResponse
import com.familyguard.shared.dto.UpsertScheduleRequest
import com.familyguard.shared.enums.ScheduleMode
import com.familyguard.shared.enums.Weekday
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeScheduleRepository(
    private var schedules: List<ScheduleResponse> = emptyList(),
) : ScheduleRepository {
    var lastCreate: UpsertScheduleRequest? = null
    var createResult: Result<ScheduleResponse>? = null

    override suspend fun createSchedule(childId: String, request: UpsertScheduleRequest): Result<ScheduleResponse> {
        lastCreate = request
        return createResult ?: Result.success(
            ScheduleResponse("new-id", childId, request.name, request.daysOfWeek, request.startTime, request.endTime, request.mode, true),
        ).also { schedules = schedules + it.getOrThrow() }
    }

    override suspend fun getSchedules(childId: String): Result<List<ScheduleResponse>> = Result.success(schedules)

    override suspend fun deleteSchedule(childId: String, scheduleId: String): Result<Unit> {
        schedules = schedules.filterNot { it.id == scheduleId }
        return Result.success(Unit)
    }
}

private class FakeCategoryRuleRepository(
    private var rules: List<CategoryRuleResponse> = emptyList(),
) : CategoryRuleRepository {
    var upsertResult: Result<CategoryRuleResponse>? = null

    override suspend fun upsertCategoryRule(childId: String, category: String, dailyLimitMinutes: Int): Result<CategoryRuleResponse> {
        upsertResult?.let { return it }
        val rule = CategoryRuleResponse("rule-1", childId, category, dailyLimitMinutes)
        rules = rules.filterNot { it.category == category } + rule
        return Result.success(rule)
    }

    override suspend fun getCategoryRules(childId: String): Result<List<CategoryRuleResponse>> = Result.success(rules)

    override suspend fun deleteCategoryRule(childId: String, category: String): Result<Unit> {
        rules = rules.filterNot { it.category == category }
        return Result.success(Unit)
    }
}

class AdvancedRulesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load populates Content with both schedules and category rules`() = runTest {
        val scheduleRepo = FakeScheduleRepository(
            listOf(ScheduleResponse("s1", "child-1", "Bedtime", listOf(Weekday.MON), "21:00", "07:00", ScheduleMode.BLOCK, true)),
        )
        val categoryRepo = FakeCategoryRuleRepository(listOf(CategoryRuleResponse("c1", "child-1", "social", 60)))
        val viewModel = AdvancedRulesViewModel(scheduleRepo, categoryRepo)

        viewModel.load("child-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AdvancedRulesUiState.Content)
        assertEquals(1, (state as AdvancedRulesUiState.Content).schedules.size)
        assertEquals(1, state.categoryRules.size)
    }

    @Test
    fun `creating a schedule with no days set surfaces an action error and skips the network call`() = runTest {
        val scheduleRepo = FakeScheduleRepository()
        val viewModel = AdvancedRulesViewModel(scheduleRepo, FakeCategoryRuleRepository())
        viewModel.load("child-1")
        advanceUntilIdle()

        viewModel.createSchedule("child-1", "Bedtime", emptySet(), "21:00", "07:00", ScheduleMode.BLOCK)
        advanceUntilIdle()

        assertEquals("Select at least one day.", viewModel.actionError.value)
        assertEquals(null, scheduleRepo.lastCreate)
    }

    @Test
    fun `creating a valid schedule reloads the list`() = runTest {
        val scheduleRepo = FakeScheduleRepository()
        val viewModel = AdvancedRulesViewModel(scheduleRepo, FakeCategoryRuleRepository())
        viewModel.load("child-1")
        advanceUntilIdle()

        viewModel.createSchedule("child-1", "Bedtime", setOf(Weekday.MON), "21:00", "07:00", ScheduleMode.BLOCK)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AdvancedRulesUiState.Content)
        assertEquals(1, (state as AdvancedRulesUiState.Content).schedules.size)
    }

    @Test
    fun `an invalid category limit surfaces an action error`() = runTest {
        val viewModel = AdvancedRulesViewModel(FakeScheduleRepository(), FakeCategoryRuleRepository())
        viewModel.load("child-1")
        advanceUntilIdle()

        viewModel.upsertCategoryRule("child-1", "social", 0)
        advanceUntilIdle()

        assertEquals("Enter a daily limit greater than 0 minutes.", viewModel.actionError.value)
    }

    @Test
    fun `a failed delete surfaces an action error`() = runTest {
        val categoryRepo = FakeCategoryRuleRepository(listOf(CategoryRuleResponse("c1", "child-1", "social", 60)))
        // Override delete to fail by wrapping.
        val failingRepo = object : CategoryRuleRepository by categoryRepo {
            override suspend fun deleteCategoryRule(childId: String, category: String): Result<Unit> =
                Result.failure(ApiException("Couldn't delete that category limit."))
        }
        val viewModel = AdvancedRulesViewModel(FakeScheduleRepository(), failingRepo)
        viewModel.load("child-1")
        advanceUntilIdle()

        viewModel.deleteCategoryRule("child-1", "social")
        advanceUntilIdle()

        assertEquals("Couldn't delete that category limit.", viewModel.actionError.value)
    }
}
