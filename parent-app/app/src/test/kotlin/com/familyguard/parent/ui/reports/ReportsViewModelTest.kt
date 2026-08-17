package com.familyguard.parent.ui.reports

import com.familyguard.parent.MainDispatcherRule
import com.familyguard.parent.data.remote.ApiException
import com.familyguard.parent.data.repository.ReportRepository
import com.familyguard.shared.dto.InsightsResponse
import com.familyguard.shared.dto.ReportResponse
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeReportRepository(
    private val reportResult: Result<ReportResponse> = Result.success(
        ReportResponse(range = "weekly", startDate = "2026-08-10", endDate = "2026-08-17", totalMinutes = 100, byApp = emptyList(), byDay = emptyList()),
    ),
    private val insightsResult: Result<InsightsResponse> = Result.success(
        InsightsResponse(currentWeekMinutes = 100, previousWeekMinutes = 50, weekOverWeekChangePercent = 100.0, topApps = emptyList(), generatedAt = "2026-08-17T00:00:00Z"),
    ),
) : ReportRepository {
    var lastRange: String? = null

    override suspend fun getReport(childId: String, range: String): Result<ReportResponse> {
        lastRange = range
        return reportResult
    }

    override suspend fun getInsights(childId: String): Result<InsightsResponse> = insightsResult
}

class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load defaults to the weekly range and populates Content`() = runTest {
        val repository = FakeReportRepository()
        val viewModel = ReportsViewModel(repository)

        viewModel.load("child-1")
        advanceUntilIdle()

        assertEquals("weekly", repository.lastRange)
        val state = viewModel.uiState.value
        assertTrue(state is ReportsUiState.Content)
        assertEquals(100, (state as ReportsUiState.Content).report.totalMinutes)
        assertEquals(100.0, state.insights.weekOverWeekChangePercent)
    }

    @Test
    fun `setRange reloads with the new range`() = runTest {
        val repository = FakeReportRepository()
        val viewModel = ReportsViewModel(repository)
        viewModel.load("child-1")
        advanceUntilIdle()

        viewModel.setRange("child-1", "monthly")
        advanceUntilIdle()

        assertEquals("monthly", repository.lastRange)
        assertEquals("monthly", viewModel.range.value)
    }

    @Test
    fun `a failed report load surfaces an Error state`() = runTest {
        val repository = FakeReportRepository(reportResult = Result.failure(ApiException("Couldn't load reports.")))
        val viewModel = ReportsViewModel(repository)

        viewModel.load("child-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReportsUiState.Error)
        assertEquals("Couldn't load reports.", (state as ReportsUiState.Error).message)
    }
}
