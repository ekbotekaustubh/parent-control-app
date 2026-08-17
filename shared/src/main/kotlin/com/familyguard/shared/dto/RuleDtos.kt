package com.familyguard.shared.dto

import com.familyguard.shared.enums.RuleType
import kotlinx.serialization.Serializable

@Serializable
data class UpsertRuleRequest(
    val ruleType: RuleType,
    val dailyLimitMinutes: Int? = null,
)

@Serializable
data class AppRuleResponse(
    val id: String,
    val childId: String,
    val packageName: String,
    val displayName: String?,
    val ruleType: RuleType,
    val dailyLimitMinutes: Int?,
    val isActive: Boolean,
)
