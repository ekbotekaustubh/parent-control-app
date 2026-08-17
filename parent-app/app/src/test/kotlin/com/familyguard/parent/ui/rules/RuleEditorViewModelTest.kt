package com.familyguard.parent.ui.rules

import com.familyguard.parent.MainDispatcherRule
import com.familyguard.parent.data.remote.ApiException
import com.familyguard.parent.data.repository.RuleRepository
import com.familyguard.parent.data.repository.ScheduleRepository
import com.familyguard.shared.dto.AppRuleResponse
import com.familyguard.shared.dto.ScheduleResponse
import com.familyguard.shared.dto.UpsertScheduleRequest
import com.familyguard.shared.enums.RuleType
import com.familyguard.shared.enums.ScheduleMode
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeRuleRepository(
    private val existingRules: List<AppRuleResponse> = emptyList(),
) : RuleRepository {
    var lastUpsert: Triple<String, RuleType, Int?>? = null
    var lastCategory: String? = null
    var lastScheduleId: String? = null
    var upsertResult: Result<AppRuleResponse>? = null

    override suspend fun upsertRule(
        childId: String,
        packageName: String,
        ruleType: RuleType,
        dailyLimitMinutes: Int?,
        category: String?,
        scheduleId: String?,
    ): Result<AppRuleResponse> {
        lastUpsert = Triple(packageName, ruleType, dailyLimitMinutes)
        lastCategory = category
        lastScheduleId = scheduleId
        return upsertResult ?: Result.success(
            AppRuleResponse(
                id = "rule-1",
                childId = childId,
                packageName = packageName,
                displayName = packageName,
                ruleType = ruleType,
                dailyLimitMinutes = dailyLimitMinutes,
                isActive = true,
                category = category,
                scheduleId = scheduleId,
            ),
        )
    }

    override suspend fun getRules(childId: String): Result<List<AppRuleResponse>> = Result.success(existingRules)

    override suspend fun deleteRule(childId: String, packageName: String): Result<Unit> = Result.success(Unit)
}

private class FakeScheduleRepository(
    private val schedules: List<ScheduleResponse> = emptyList(),
) : ScheduleRepository {
    override suspend fun createSchedule(childId: String, request: UpsertScheduleRequest): Result<ScheduleResponse> =
        error("not used in these tests")

    override suspend fun getSchedules(childId: String): Result<List<ScheduleResponse>> = Result.success(schedules)

    override suspend fun deleteSchedule(childId: String, scheduleId: String): Result<Unit> = Result.success(Unit)
}

class RuleEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(ruleRepository: RuleRepository, scheduleRepository: ScheduleRepository = FakeScheduleRepository()) =
        RuleEditorViewModel(ruleRepository, scheduleRepository)

    @Test
    fun `load populates Ready state and defaults to BLOCK for an app with no existing rule`() = runTest {
        val viewModel = viewModel(FakeRuleRepository())

        viewModel.load("child-1")
        advanceUntilIdle()

        assertEquals(RuleEditorUiState.Ready, viewModel.uiState.value)
        assertEquals(RuleType.BLOCK, viewModel.form.value.ruleType)
    }

    @Test
    fun `selecting an app with an existing ALLOW rule prefills the form`() = runTest {
        val existing = AppRuleResponse(
            id = "rule-1",
            childId = "child-1",
            packageName = CommonApp.YOUTUBE.packageName,
            displayName = "YouTube",
            ruleType = RuleType.ALLOW,
            dailyLimitMinutes = 45,
            isActive = true,
            category = "social",
            scheduleId = null,
        )
        val viewModel = viewModel(FakeRuleRepository(existingRules = listOf(existing)))

        viewModel.load("child-1")
        advanceUntilIdle()
        viewModel.selectApp(CommonApp.YOUTUBE)

        assertEquals(RuleType.ALLOW, viewModel.form.value.ruleType)
        assertEquals("45", viewModel.form.value.dailyLimitMinutesText)
        assertEquals("social", viewModel.form.value.category)
    }

    @Test
    fun `saving an ALLOW rule without a limit produces a validation error and skips the network call`() = runTest {
        val repository = FakeRuleRepository()
        val viewModel = viewModel(repository)
        viewModel.load("child-1")
        advanceUntilIdle()

        viewModel.setRuleType(RuleType.ALLOW)
        viewModel.save("child-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is RuleEditorUiState.Error)
        assertEquals(null, repository.lastUpsert)
    }

    @Test
    fun `saving a BLOCK rule succeeds and transitions to Saved`() = runTest {
        val repository = FakeRuleRepository()
        val viewModel = viewModel(repository)
        viewModel.load("child-1")
        advanceUntilIdle()

        viewModel.save("child-1")
        advanceUntilIdle()

        assertEquals(RuleEditorUiState.Saved, viewModel.uiState.value)
        assertEquals(Triple(CommonApp.YOUTUBE.packageName, RuleType.BLOCK, null), repository.lastUpsert)
    }

    @Test
    fun `setting a category and schedule passes them through to the repository on save`() = runTest {
        val schedule = ScheduleResponse(
            id = "sched-1",
            childId = "child-1",
            name = "Study",
            daysOfWeek = emptyList(),
            startTime = "09:00",
            endTime = "10:00",
            mode = ScheduleMode.ALLOW_ONLY,
            isActive = true,
        )
        val repository = FakeRuleRepository()
        val viewModel = viewModel(repository, FakeScheduleRepository(listOf(schedule)))
        viewModel.load("child-1")
        advanceUntilIdle()

        viewModel.setCategory("social")
        viewModel.setSelectedScheduleId(schedule.id)
        viewModel.save("child-1")
        advanceUntilIdle()

        assertEquals("social", repository.lastCategory)
        assertEquals("sched-1", repository.lastScheduleId)
        assertEquals(listOf(schedule), viewModel.availableSchedules.value)
    }

    @Test
    fun `a failed save surfaces the repository's error message`() = runTest {
        val repository = FakeRuleRepository()
        repository.upsertResult = Result.failure(ApiException("Couldn't save this rule."))
        val viewModel = viewModel(repository)
        viewModel.load("child-1")
        advanceUntilIdle()

        viewModel.save("child-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is RuleEditorUiState.Error)
        assertEquals("Couldn't save this rule.", (state as RuleEditorUiState.Error).message)
    }
}
