package com.familyguard.shared.dto

import com.familyguard.shared.enums.RuleType
import kotlinx.serialization.Serializable

@Serializable
data class UpsertRuleRequest(
    val ruleType: RuleType,
    val dailyLimitMinutes: Int? = null,
    /** Tags the app's catalog row with this category (`apps.category`) - see docs/roadmap.md's "Category-level rules". Omit to leave the app's existing category (if any) untouched. */
    val category: String? = null,
    /** Ties this rule to a schedule's window (docs/roadmap.md's "Schedules") - null (the default) means the rule always applies. */
    val scheduleId: String? = null,
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
    val category: String?,
    val scheduleId: String?,
)
