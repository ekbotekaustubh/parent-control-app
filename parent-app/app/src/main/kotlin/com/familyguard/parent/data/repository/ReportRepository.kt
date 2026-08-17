package com.familyguard.parent.data.repository

import com.familyguard.parent.data.remote.ErrorMapper
import com.familyguard.parent.data.remote.ParentApiService
import com.familyguard.shared.dto.InsightsResponse
import com.familyguard.shared.dto.ReportResponse
import javax.inject.Inject
import javax.inject.Singleton

interface ReportRepository {
    suspend fun getReport(childId: String, range: String): Result<ReportResponse>
    suspend fun getInsights(childId: String): Result<InsightsResponse>
}

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val api: ParentApiService,
    private val errorMapper: ErrorMapper,
) : ReportRepository {

    override suspend fun getReport(childId: String, range: String): Result<ReportResponse> = runCatching {
        api.getReport(childId, range)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun getInsights(childId: String): Result<InsightsResponse> = runCatching {
        api.getInsights(childId)
    }.recoverCatching { throw errorMapper.toApiException(it) }
}
