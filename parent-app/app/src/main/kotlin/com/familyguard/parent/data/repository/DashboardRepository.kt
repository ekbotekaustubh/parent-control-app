package com.familyguard.parent.data.repository

import com.familyguard.parent.data.local.db.DashboardCacheDao
import com.familyguard.parent.data.local.db.DashboardCacheEntity
import com.familyguard.parent.data.local.db.toCacheEntity
import com.familyguard.parent.data.remote.ErrorMapper
import com.familyguard.parent.data.remote.ParentApiService
import com.familyguard.shared.dto.DashboardResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

interface DashboardRepository {
    suspend fun refreshDashboard(childId: String): Result<DashboardResponse>
    fun observeCachedDashboard(childId: String): Flow<DashboardCacheEntity?>
    suspend fun getCachedDashboard(childId: String): DashboardCacheEntity?
    suspend fun getAllCachedDashboards(): List<DashboardCacheEntity>
}

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val api: ParentApiService,
    private val dashboardCacheDao: DashboardCacheDao,
    private val json: Json,
    private val errorMapper: ErrorMapper,
) : DashboardRepository {

    override suspend fun refreshDashboard(childId: String): Result<DashboardResponse> = runCatching {
        val response = api.getDashboard(childId)
        dashboardCacheDao.upsert(response.toCacheEntity(childId, json, System.currentTimeMillis()))
        response
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override fun observeCachedDashboard(childId: String): Flow<DashboardCacheEntity?> =
        dashboardCacheDao.observe(childId)

    override suspend fun getCachedDashboard(childId: String): DashboardCacheEntity? =
        dashboardCacheDao.get(childId)

    override suspend fun getAllCachedDashboards(): List<DashboardCacheEntity> =
        dashboardCacheDao.getAll()
}
