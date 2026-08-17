package com.familyguard.parent.ui.advancedrules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.parent.data.repository.CategoryRuleRepository
import com.familyguard.parent.data.repository.ScheduleRepository
import com.familyguard.shared.dto.CategoryRuleResponse
import com.familyguard.shared.dto.ScheduleResponse
import com.familyguard.shared.dto.UpsertScheduleRequest
import com.familyguard.shared.enums.ScheduleMode
import com.familyguard.shared.enums.Weekday
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Combines schedules and category rules into one screen - both are "advanced"/less-common rule configuration, so one nav destination covers both rather than two near-empty screens. */
sealed interface AdvancedRulesUiState {
    data object Loading : AdvancedRulesUiState
    data class Content(val schedules: List<ScheduleResponse>, val categoryRules: List<CategoryRuleResponse>) : AdvancedRulesUiState
    data class Error(val message: String) : AdvancedRulesUiState
}

@HiltViewModel
class AdvancedRulesViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val categoryRuleRepository: CategoryRuleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdvancedRulesUiState>(AdvancedRulesUiState.Loading)
    val uiState: StateFlow<AdvancedRulesUiState> = _uiState.asStateFlow()

    /** Transient error from a create/delete action - the list itself keeps showing (no full-screen error) since it's still valid. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun load(childId: String) {
        viewModelScope.launch {
            _uiState.value = AdvancedRulesUiState.Loading
            val schedulesResult = scheduleRepository.getSchedules(childId)
            val categoryRulesResult = categoryRuleRepository.getCategoryRules(childId)
            val schedules = schedulesResult.getOrNull()
            val categoryRules = categoryRulesResult.getOrNull()
            _uiState.value = if (schedules != null && categoryRules != null) {
                AdvancedRulesUiState.Content(schedules, categoryRules)
            } else {
                val error = schedulesResult.exceptionOrNull() ?: categoryRulesResult.exceptionOrNull()
                AdvancedRulesUiState.Error(error?.message ?: "Couldn't load schedules and category limits.")
            }
        }
    }

    fun createSchedule(childId: String, name: String, days: Set<Weekday>, startTime: String, endTime: String, mode: ScheduleMode) {
        if (name.isBlank()) {
            _actionError.value = "Give the schedule a name."
            return
        }
        if (days.isEmpty()) {
            _actionError.value = "Select at least one day."
            return
        }
        viewModelScope.launch {
            scheduleRepository.createSchedule(childId, UpsertScheduleRequest(name, days.toList(), startTime, endTime, mode))
                .onSuccess { load(childId) }
                .onFailure { _actionError.value = it.message ?: "Couldn't create that schedule." }
        }
    }

    fun deleteSchedule(childId: String, scheduleId: String) {
        viewModelScope.launch {
            scheduleRepository.deleteSchedule(childId, scheduleId)
                .onSuccess { load(childId) }
                .onFailure { _actionError.value = it.message ?: "Couldn't delete that schedule." }
        }
    }

    fun upsertCategoryRule(childId: String, category: String, dailyLimitMinutes: Int) {
        if (category.isBlank()) {
            _actionError.value = "Enter a category name."
            return
        }
        if (dailyLimitMinutes <= 0) {
            _actionError.value = "Enter a daily limit greater than 0 minutes."
            return
        }
        viewModelScope.launch {
            categoryRuleRepository.upsertCategoryRule(childId, category, dailyLimitMinutes)
                .onSuccess { load(childId) }
                .onFailure { _actionError.value = it.message ?: "Couldn't save that category limit." }
        }
    }

    fun deleteCategoryRule(childId: String, category: String) {
        viewModelScope.launch {
            categoryRuleRepository.deleteCategoryRule(childId, category)
                .onSuccess { load(childId) }
                .onFailure { _actionError.value = it.message ?: "Couldn't delete that category limit." }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }
}
