package com.familyguard.parent.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.parent.data.repository.ReportRepository
import com.familyguard.shared.dto.InsightsResponse
import com.familyguard.shared.dto.ReportResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data class Content(val report: ReportResponse, val insights: InsightsResponse) : ReportsUiState
    data class Error(val message: String) : ReportsUiState
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportsUiState>(ReportsUiState.Loading)
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val _range = MutableStateFlow("weekly")
    val range: StateFlow<String> = _range.asStateFlow()

    fun load(childId: String) {
        viewModelScope.launch {
            _uiState.value = ReportsUiState.Loading
            // Insights is always the rolling 7-day window regardless of the report range
            // picker - it's a distinct, fixed "week over week" figure, not itself range-able.
            val reportResult = reportRepository.getReport(childId, _range.value)
            val insightsResult = reportRepository.getInsights(childId)
            val report = reportResult.getOrNull()
            val insights = insightsResult.getOrNull()
            _uiState.value = if (report != null && insights != null) {
                ReportsUiState.Content(report, insights)
            } else {
                val error = reportResult.exceptionOrNull() ?: insightsResult.exceptionOrNull()
                ReportsUiState.Error(error?.message ?: "Couldn't load reports.")
            }
        }
    }

    fun setRange(childId: String, range: String) {
        _range.value = range
        load(childId)
    }
}
