package com.familyguard.shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpsertCategoryRuleRequest(
    val dailyLimitMinutes: Int,
)

@Serializable
data class CategoryRuleResponse(
    val id: String,
    val childId: String,
    val category: String,
    val dailyLimitMinutes: Int,
)
