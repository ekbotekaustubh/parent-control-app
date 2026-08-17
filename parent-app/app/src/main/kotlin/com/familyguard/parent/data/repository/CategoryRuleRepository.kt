package com.familyguard.parent.data.repository

import com.familyguard.parent.data.remote.ErrorMapper
import com.familyguard.parent.data.remote.ParentApiService
import com.familyguard.shared.dto.CategoryRuleResponse
import com.familyguard.shared.dto.UpsertCategoryRuleRequest
import javax.inject.Inject
import javax.inject.Singleton

interface CategoryRuleRepository {
    suspend fun upsertCategoryRule(childId: String, category: String, dailyLimitMinutes: Int): Result<CategoryRuleResponse>
    suspend fun getCategoryRules(childId: String): Result<List<CategoryRuleResponse>>
    suspend fun deleteCategoryRule(childId: String, category: String): Result<Unit>
}

@Singleton
class CategoryRuleRepositoryImpl @Inject constructor(
    private val api: ParentApiService,
    private val errorMapper: ErrorMapper,
) : CategoryRuleRepository {

    override suspend fun upsertCategoryRule(childId: String, category: String, dailyLimitMinutes: Int): Result<CategoryRuleResponse> = runCatching {
        api.upsertCategoryRule(childId, category, UpsertCategoryRuleRequest(dailyLimitMinutes))
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun getCategoryRules(childId: String): Result<List<CategoryRuleResponse>> = runCatching {
        api.getCategoryRules(childId)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun deleteCategoryRule(childId: String, category: String): Result<Unit> = runCatching {
        api.deleteCategoryRule(childId, category)
        Unit
    }.recoverCatching { throw errorMapper.toApiException(it) }
}
