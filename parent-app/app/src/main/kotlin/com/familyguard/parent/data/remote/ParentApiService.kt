package com.familyguard.parent.data.remote

import com.familyguard.shared.dto.AccessRequestResponse
import com.familyguard.shared.dto.AppRuleResponse
import com.familyguard.shared.dto.AuthTokensResponse
import com.familyguard.shared.dto.CategoryRuleResponse
import com.familyguard.shared.dto.ChildResponse
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.DashboardResponse
import com.familyguard.shared.dto.DeviceResponse
import com.familyguard.shared.dto.InsightsResponse
import com.familyguard.shared.dto.LoginRequest
import com.familyguard.shared.dto.LogoutRequest
import com.familyguard.shared.dto.PairingCodeResponse
import com.familyguard.shared.dto.RefreshRequest
import com.familyguard.shared.dto.ReportResponse
import com.familyguard.shared.dto.ResolveAccessRequestRequest
import com.familyguard.shared.dto.ScheduleResponse
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.SignupResponse
import com.familyguard.shared.dto.UpsertCategoryRuleRequest
import com.familyguard.shared.dto.UpsertRuleRequest
import com.familyguard.shared.dto.UpsertScheduleRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit annotations require literal path strings (compile-time constant folding across
 * module boundaries isn't reliable), so each endpoint's path is written out literally with
 * a comment naming the corresponding `com.familyguard.shared.ApiPaths` constant for
 * traceability — the two must be kept in sync by hand.
 *
 * Only endpoints this app actually calls are declared here: parent-facing auth/children/
 * pairing/rules/dashboard routes. The device-facing routes (`/pairing/claim`,
 * `/device/config`, `/device/usage-sync`, `/device/heartbeat`,
 * `/devices/{id}/token-exchange`, `/devices/token/refresh`) belong to child-app, not here —
 * this app never talks to the child device directly (docs/architecture.md).
 */
interface ParentApiService {

    // ---- Auth (parent) ----

    // ApiPaths.AUTH_SIGNUP
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): SignupResponse

    // ApiPaths.AUTH_LOGIN
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthTokensResponse

    // ApiPaths.AUTH_REFRESH — called by both the app's normal flows and, via a separate
    // non-authenticated Retrofit instance, by AuthAuthenticator itself (see di/NetworkModule.kt).
    @POST("auth/refresh")
    suspend fun refreshParentToken(@Body request: RefreshRequest): AuthTokensResponse

    // ApiPaths.AUTH_LOGOUT
    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    // ---- Children ----

    // ApiPaths.CHILDREN
    @POST("children")
    suspend fun createChild(@Body request: CreateChildRequest): ChildResponse

    // ApiPaths.CHILDREN
    @GET("children")
    suspend fun getChildren(): List<ChildResponse>

    // ApiPaths.CHILD_BY_ID
    @GET("children/{childId}")
    suspend fun getChild(@Path("childId") childId: String): ChildResponse

    // ---- Pairing ----

    // ApiPaths.PAIRING_CODES
    @POST("children/{childId}/pairing-codes")
    suspend fun generatePairingCode(@Path("childId") childId: String): PairingCodeResponse

    // ApiPaths.DEVICE_APPROVE
    @POST("devices/{deviceId}/approve")
    suspend fun approveDevice(@Path("deviceId") deviceId: String): DeviceResponse

    // ApiPaths.DEVICES
    @GET("devices")
    suspend fun getDevices(@Query("childId") childId: String): List<DeviceResponse>

    // ApiPaths.DEVICE_REVOKE
    @POST("devices/{deviceId}/revoke")
    suspend fun revokeDevice(@Path("deviceId") deviceId: String): Response<Unit>

    // ---- Rules ----

    // ApiPaths.CHILD_APP_RULE
    @PUT("children/{childId}/apps/{packageName}/rule")
    suspend fun upsertRule(
        @Path("childId") childId: String,
        @Path("packageName") packageName: String,
        @Body request: UpsertRuleRequest,
    ): AppRuleResponse

    // ApiPaths.CHILD_RULES
    @GET("children/{childId}/rules")
    suspend fun getRules(@Path("childId") childId: String): List<AppRuleResponse>

    // ApiPaths.CHILD_APP_RULE
    @DELETE("children/{childId}/apps/{packageName}/rule")
    suspend fun deleteRule(
        @Path("childId") childId: String,
        @Path("packageName") packageName: String,
    ): Response<Unit>

    // ---- Dashboard ----

    // ApiPaths.CHILD_DASHBOARD
    @GET("children/{childId}/dashboard")
    suspend fun getDashboard(@Path("childId") childId: String): DashboardResponse

    // ---- Access requests ----

    // ApiPaths.ACCESS_REQUESTS — pending requests across every child owned by this parent.
    @GET("access-requests")
    suspend fun getAccessRequests(): List<AccessRequestResponse>

    // ApiPaths.ACCESS_REQUEST_RESOLVE
    @POST("access-requests/{requestId}/resolve")
    suspend fun resolveAccessRequest(
        @Path("requestId") requestId: String,
        @Body request: ResolveAccessRequestRequest,
    ): AccessRequestResponse

    // ---- Category rules ----

    // ApiPaths.CHILD_CATEGORY_RULE
    @PUT("children/{childId}/categories/{category}/rule")
    suspend fun upsertCategoryRule(
        @Path("childId") childId: String,
        @Path("category") category: String,
        @Body request: UpsertCategoryRuleRequest,
    ): CategoryRuleResponse

    // ApiPaths.CHILD_CATEGORY_RULES
    @GET("children/{childId}/category-rules")
    suspend fun getCategoryRules(@Path("childId") childId: String): List<CategoryRuleResponse>

    // ApiPaths.CHILD_CATEGORY_RULE
    @DELETE("children/{childId}/categories/{category}/rule")
    suspend fun deleteCategoryRule(
        @Path("childId") childId: String,
        @Path("category") category: String,
    ): Response<Unit>

    // ---- Schedules ----

    // ApiPaths.CHILD_SCHEDULES
    @POST("children/{childId}/schedules")
    suspend fun createSchedule(@Path("childId") childId: String, @Body request: UpsertScheduleRequest): ScheduleResponse

    // ApiPaths.CHILD_SCHEDULES
    @GET("children/{childId}/schedules")
    suspend fun getSchedules(@Path("childId") childId: String): List<ScheduleResponse>

    // ApiPaths.CHILD_SCHEDULE_BY_ID
    @DELETE("children/{childId}/schedules/{scheduleId}")
    suspend fun deleteSchedule(@Path("childId") childId: String, @Path("scheduleId") scheduleId: String): Response<Unit>

    // ---- Reports & insights ----

    // ApiPaths.CHILD_REPORT
    @GET("children/{childId}/report")
    suspend fun getReport(
        @Path("childId") childId: String,
        @Query("range") range: String,
        @Query("anchor") anchor: String? = null,
    ): ReportResponse

    // ApiPaths.CHILD_INSIGHTS
    @GET("children/{childId}/insights")
    suspend fun getInsights(@Path("childId") childId: String): InsightsResponse
}
