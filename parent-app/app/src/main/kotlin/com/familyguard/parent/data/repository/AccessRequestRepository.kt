package com.familyguard.parent.data.repository

import com.familyguard.parent.data.remote.ErrorMapper
import com.familyguard.parent.data.remote.ParentApiService
import com.familyguard.shared.dto.AccessRequestResponse
import com.familyguard.shared.dto.ResolveAccessRequestRequest
import javax.inject.Inject
import javax.inject.Singleton

interface AccessRequestRepository {
    suspend fun getPendingRequests(): Result<List<AccessRequestResponse>>
    suspend fun resolve(requestId: String, approve: Boolean, resolvedMinutes: Int? = null): Result<AccessRequestResponse>
}

@Singleton
class AccessRequestRepositoryImpl @Inject constructor(
    private val api: ParentApiService,
    private val errorMapper: ErrorMapper,
) : AccessRequestRepository {

    override suspend fun getPendingRequests(): Result<List<AccessRequestResponse>> = runCatching {
        api.getAccessRequests()
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun resolve(requestId: String, approve: Boolean, resolvedMinutes: Int?): Result<AccessRequestResponse> = runCatching {
        api.resolveAccessRequest(requestId, ResolveAccessRequestRequest(approve, resolvedMinutes))
    }.recoverCatching { throw errorMapper.toApiException(it) }
}
