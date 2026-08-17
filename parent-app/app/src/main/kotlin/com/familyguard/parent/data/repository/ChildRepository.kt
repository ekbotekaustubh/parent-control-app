package com.familyguard.parent.data.repository

import com.familyguard.parent.data.local.db.ChildCacheDao
import com.familyguard.parent.data.local.db.ChildCacheEntity
import com.familyguard.parent.data.remote.ErrorMapper
import com.familyguard.parent.data.remote.ParentApiService
import com.familyguard.shared.dto.ChildResponse
import com.familyguard.shared.dto.CreateChildRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ChildRepository {
    suspend fun createChild(name: String, birthYear: Int?): Result<ChildResponse>
    suspend fun refreshChildren(): Result<List<ChildResponse>>
    suspend fun getChild(childId: String): Result<ChildResponse>
    fun observeCachedChildren(): Flow<List<ChildResponse>>
}

@Singleton
class ChildRepositoryImpl @Inject constructor(
    private val api: ParentApiService,
    private val childCacheDao: ChildCacheDao,
    private val errorMapper: ErrorMapper,
) : ChildRepository {

    override suspend fun createChild(name: String, birthYear: Int?): Result<ChildResponse> = runCatching {
        val response = api.createChild(CreateChildRequest(name = name, birthYear = birthYear))
        childCacheDao.upsertAll(listOf(response.toCacheEntity()))
        response
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun refreshChildren(): Result<List<ChildResponse>> = runCatching {
        val children = api.getChildren()
        childCacheDao.replaceAll(children.map { it.toCacheEntity() })
        children
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun getChild(childId: String): Result<ChildResponse> = runCatching {
        val response = api.getChild(childId)
        childCacheDao.upsertAll(listOf(response.toCacheEntity()))
        response
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override fun observeCachedChildren(): Flow<List<ChildResponse>> =
        childCacheDao.observeAll().map { entities -> entities.map { it.toDto() } }
}

private fun ChildResponse.toCacheEntity(): ChildCacheEntity = ChildCacheEntity(
    id = id,
    parentId = parentId,
    name = name,
    birthYear = birthYear,
    configVersion = configVersion,
    status = status,
    createdAt = createdAt,
)

private fun ChildCacheEntity.toDto(): ChildResponse = ChildResponse(
    id = id,
    parentId = parentId,
    name = name,
    birthYear = birthYear,
    configVersion = configVersion,
    status = status,
    createdAt = createdAt,
)
