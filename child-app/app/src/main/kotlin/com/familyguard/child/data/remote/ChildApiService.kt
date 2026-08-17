package com.familyguard.child.data.remote

import com.familyguard.shared.ApiPaths
import com.familyguard.shared.dto.AccessRequestResponse
import com.familyguard.shared.dto.ClaimRequest
import com.familyguard.shared.dto.ClaimResponse
import com.familyguard.shared.dto.ClaimStatusResponse
import com.familyguard.shared.dto.CreateAccessRequestRequest
import com.familyguard.shared.dto.DeviceConfigResponse
import com.familyguard.shared.dto.DeviceTokenRefreshRequest
import com.familyguard.shared.dto.DeviceTokenRefreshResponse
import com.familyguard.shared.dto.DeviceTokensResponse
import com.familyguard.shared.dto.HeartbeatResponse
import com.familyguard.shared.dto.TokenExchangeRequest
import com.familyguard.shared.dto.UsageSyncRequest
import com.familyguard.shared.dto.UsageSyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit annotations require string literals, not the `ApiPaths` constants directly, so
 * each endpoint below carries a comment naming the exact `ApiPaths` constant it must stay
 * in sync with, for traceability back to the shared contract.
 */
interface ChildApiService {

    // ApiPaths.PAIRING_CLAIM = "pairing/claim" — unauthenticated.
    @POST("pairing/claim")
    suspend fun claimPairingCode(@Body request: ClaimRequest): ClaimResponse

    // ApiPaths.PAIRING_CLAIM_STATUS = "pairing/claim/{deviceId}/status" — unauthenticated,
    // guarded by the X-Claim-Ticket header instead (ApiPaths.CLAIM_TICKET_HEADER).
    @GET("pairing/claim/{deviceId}/status")
    suspend fun getClaimStatus(
        @Path("deviceId") deviceId: String,
        @Header(ApiPaths.CLAIM_TICKET_HEADER) claimTicket: String,
    ): ClaimStatusResponse

    // ApiPaths.DEVICE_TOKEN_EXCHANGE = "devices/{deviceId}/token-exchange" — unauthenticated
    // (the claim ticket in the body is the credential).
    @POST("devices/{deviceId}/token-exchange")
    suspend fun exchangeToken(
        @Path("deviceId") deviceId: String,
        @Body request: TokenExchangeRequest,
    ): DeviceTokensResponse

    // ApiPaths.DEVICE_TOKEN_REFRESH = "devices/token/refresh" — unauthenticated (refresh
    // token itself is the credential); called from AuthInterceptor's Authenticator, not
    // through the normal authenticated call path.
    @POST("devices/token/refresh")
    suspend fun refreshDeviceToken(@Body request: DeviceTokenRefreshRequest): Response<DeviceTokenRefreshResponse>

    // ApiPaths.DEVICE_CONFIG = "device/config" — device-authenticated, deviceId/childId from JWT.
    @GET("device/config")
    suspend fun getDeviceConfig(): DeviceConfigResponse

    // ApiPaths.DEVICE_USAGE_SYNC = "device/usage-sync" — device-authenticated.
    @POST("device/usage-sync")
    suspend fun syncUsage(@Body request: UsageSyncRequest): UsageSyncResponse

    // ApiPaths.DEVICE_HEARTBEAT = "device/heartbeat" — device-authenticated.
    @POST("device/heartbeat")
    suspend fun heartbeat(): HeartbeatResponse

    // ApiPaths.DEVICE_ACCESS_REQUESTS = "device/access-requests" — device-authenticated.
    @POST("device/access-requests")
    suspend fun createAccessRequest(@Body request: CreateAccessRequestRequest): AccessRequestResponse
}
