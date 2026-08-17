package com.familyguard.parent.data.repository

import com.familyguard.parent.data.remote.ErrorMapper
import com.familyguard.parent.data.remote.ParentApiService
import com.familyguard.shared.dto.DeviceResponse
import com.familyguard.shared.dto.PairingCodeResponse
import com.familyguard.shared.enums.DeviceStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generate a pairing code, poll for the child device to claim it (surfaces as a device row
 * with status `pending_approval`), and approve it. This app never talks to the child device
 * directly (docs/architecture.md) — "polling for a pending device" here means polling the
 * backend's `GET /devices?childId=...`, not any peer-to-peer channel.
 */
interface PairingRepository {
    suspend fun generateCode(childId: String): Result<PairingCodeResponse>
    suspend fun getDevices(childId: String): Result<List<DeviceResponse>>
    suspend fun findPendingApprovalDevice(childId: String): Result<DeviceResponse?>
    suspend fun approveDevice(deviceId: String): Result<DeviceResponse>
    suspend fun revokeDevice(deviceId: String): Result<Unit>
}

@Singleton
class PairingRepositoryImpl @Inject constructor(
    private val api: ParentApiService,
    private val errorMapper: ErrorMapper,
) : PairingRepository {

    override suspend fun generateCode(childId: String): Result<PairingCodeResponse> = runCatching {
        api.generatePairingCode(childId)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun getDevices(childId: String): Result<List<DeviceResponse>> = runCatching {
        api.getDevices(childId)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun findPendingApprovalDevice(childId: String): Result<DeviceResponse?> =
        getDevices(childId).map { devices ->
            devices.filter { it.status == DeviceStatus.PENDING_APPROVAL }
                // Most recently paired should be the one currently being claimed.
                .maxByOrNull { it.pairedAt.orEmpty() }
        }

    override suspend fun approveDevice(deviceId: String): Result<DeviceResponse> = runCatching {
        api.approveDevice(deviceId)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun revokeDevice(deviceId: String): Result<Unit> = runCatching {
        api.revokeDevice(deviceId)
        Unit
    }.recoverCatching { throw errorMapper.toApiException(it) }
}
