package com.familyguard.parent.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyguard.parent.data.repository.RuleRepository
import com.familyguard.shared.dto.AppRuleResponse
import com.familyguard.shared.enums.RuleType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuleEditorFormState(
    val selectedApp: CommonApp = CommonApp.entries.first(),
    val ruleType: RuleType = RuleType.BLOCK,
    val dailyLimitMinutesText: String = "",
)

sealed interface RuleEditorUiState {
    data object Loading : RuleEditorUiState
    data object Ready : RuleEditorUiState
    data object Saving : RuleEditorUiState
    data object Saved : RuleEditorUiState
    data class Error(val message: String) : RuleEditorUiState
}

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RuleEditorUiState>(RuleEditorUiState.Loading)
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    private val _form = MutableStateFlow(RuleEditorFormState())
    val form: StateFlow<RuleEditorFormState> = _form.asStateFlow()

    private val _existingRules = MutableStateFlow<Map<String, AppRuleResponse>>(emptyMap())

    fun load(childId: String) {
        viewModelScope.launch {
            _uiState.value = RuleEditorUiState.Loading
            val result = ruleRepository.getRules(childId)
            result.fold(
                onSuccess = { rules ->
                    _existingRules.value = rules.associateBy { it.packageName }
                    applyExistingRuleFor(_form.value.selectedApp)
                    _uiState.value = RuleEditorUiState.Ready
                },
                onFailure = { error ->
                    _uiState.value = RuleEditorUiState.Error(error.message ?: "Couldn't load current rules.")
                },
            )
        }
    }

    fun selectApp(app: CommonApp) {
        _form.update { it.copy(selectedApp = app) }
        applyExistingRuleFor(app)
    }

    private fun applyExistingRuleFor(app: CommonApp) {
        val existing = _existingRules.value[app.packageName]
        _form.update {
            it.copy(
                ruleType = existing?.ruleType ?: RuleType.BLOCK,
                dailyLimitMinutesText = existing?.dailyLimitMinutes?.toString().orEmpty(),
            )
        }
    }

    fun setRuleType(ruleType: RuleType) = _form.update { it.copy(ruleType = ruleType) }

    fun setDailyLimitMinutesText(value: String) =
        _form.update { it.copy(dailyLimitMinutesText = value.filter(Char::isDigit).take(4)) }

    fun save(childId: String) {
        val current = _form.value
        val limitMinutes = current.dailyLimitMinutesText.toIntOrNull()
        if (current.ruleType == RuleType.ALLOW && (limitMinutes == null || limitMinutes <= 0)) {
            _uiState.value = RuleEditorUiState.Error("Enter a daily limit greater than 0 minutes.")
            return
        }

        viewModelScope.launch {
            _uiState.value = RuleEditorUiState.Saving
            val result = ruleRepository.upsertRule(
                childId = childId,
                packageName = current.selectedApp.packageName,
                ruleType = current.ruleType,
                dailyLimitMinutes = limitMinutes,
            )
            _uiState.value = result.fold(
                onSuccess = { RuleEditorUiState.Saved },
                onFailure = { error -> RuleEditorUiState.Error(error.message ?: "Couldn't save this rule.") },
            )
        }
    }
}
