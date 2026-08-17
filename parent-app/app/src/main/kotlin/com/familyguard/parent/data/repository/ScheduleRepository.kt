package com.familyguard.parent.data.repository

import com.familyguard.parent.data.remote.ErrorMapper
import com.familyguard.parent.data.remote.ParentApiService
import com.familyguard.shared.dto.ScheduleResponse
import com.familyguard.shared.dto.UpsertScheduleRequest
import javax.inject.Inject
import javax.inject.Singleton

interface ScheduleRepository {
    suspend fun createSchedule(childId: String, request: UpsertScheduleRequest): Result<ScheduleResponse>
    suspend fun getSchedules(childId: String): Result<List<ScheduleResponse>>
    suspend fun deleteSchedule(childId: String, scheduleId: String): Result<Unit>
}

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val api: ParentApiService,
    private val errorMapper: ErrorMapper,
) : ScheduleRepository {

    override suspend fun createSchedule(childId: String, request: UpsertScheduleRequest): Result<ScheduleResponse> = runCatching {
        api.createSchedule(childId, request)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun getSchedules(childId: String): Result<List<ScheduleResponse>> = runCatching {
        api.getSchedules(childId)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun deleteSchedule(childId: String, scheduleId: String): Result<Unit> = runCatching {
        api.deleteSchedule(childId, scheduleId)
        Unit
    }.recoverCatching { throw errorMapper.toApiException(it) }
}
