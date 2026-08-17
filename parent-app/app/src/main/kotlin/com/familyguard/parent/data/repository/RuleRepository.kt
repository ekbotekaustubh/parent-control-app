package com.familyguard.parent.data.repository

import com.familyguard.parent.data.remote.ErrorMapper
import com.familyguard.parent.data.remote.ParentApiService
import com.familyguard.shared.dto.AppRuleResponse
import com.familyguard.shared.dto.UpsertRuleRequest
import com.familyguard.shared.enums.RuleType
import javax.inject.Inject
import javax.inject.Singleton

interface RuleRepository {
    suspend fun upsertRule(childId: String, packageName: String, ruleType: RuleType, dailyLimitMinutes: Int?): Result<AppRuleResponse>
    suspend fun getRules(childId: String): Result<List<AppRuleResponse>>
    suspend fun deleteRule(childId: String, packageName: String): Result<Unit>
}

@Singleton
class RuleRepositoryImpl @Inject constructor(
    private val api: ParentApiService,
    private val errorMapper: ErrorMapper,
) : RuleRepository {

    override suspend fun upsertRule(
        childId: String,
        packageName: String,
        ruleType: RuleType,
        dailyLimitMinutes: Int?,
    ): Result<AppRuleResponse> = runCatching {
        api.upsertRule(
            childId = childId,
            packageName = packageName,
            request = UpsertRuleRequest(
                ruleType = ruleType,
                // Only meaningful for ALLOW per docs/database-schema.md; omit for BLOCK so
                // a stale limit from a prior ALLOW rule can't linger server-side.
                dailyLimitMinutes = if (ruleType == RuleType.ALLOW) dailyLimitMinutes else null,
            ),
        )
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun getRules(childId: String): Result<List<AppRuleResponse>> = runCatching {
        api.getRules(childId)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun deleteRule(childId: String, packageName: String): Result<Unit> = runCatching {
        api.deleteRule(childId, packageName)
        Unit
    }.recoverCatching { throw errorMapper.toApiException(it) }
}
