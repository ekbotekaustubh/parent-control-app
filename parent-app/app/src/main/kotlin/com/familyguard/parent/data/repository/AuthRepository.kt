package com.familyguard.parent.data.repository

import com.familyguard.parent.data.local.JwtSubjectDecoder
import com.familyguard.parent.data.local.TokenStore
import com.familyguard.parent.data.remote.ErrorMapper
import com.familyguard.parent.data.remote.ParentApiService
import com.familyguard.shared.dto.LoginRequest
import com.familyguard.shared.dto.LogoutRequest
import com.familyguard.shared.dto.SignupRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Interface (not a concrete class) so ViewModel unit tests can inject a fake implementation. */
interface AuthRepository {
    suspend fun signup(email: String, password: String, displayName: String): Result<Unit>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun logout(): Result<Unit>
    fun isLoggedIn(): Boolean
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: ParentApiService,
    private val tokenStore: TokenStore,
    private val errorMapper: ErrorMapper,
) : AuthRepository {

    override suspend fun signup(email: String, password: String, displayName: String): Result<Unit> = runCatching {
        val response = api.signup(SignupRequest(email = email, password = password, displayName = displayName))
        tokenStore.saveSession(response.parentId, response.accessToken, response.refreshToken)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val response = api.login(LoginRequest(email = email, password = password))
        // /auth/login (unlike /auth/signup) doesn't return a parentId — decode it from the
        // JWT's `sub` claim for local display purposes only (see JwtSubjectDecoder's kdoc).
        val parentId = JwtSubjectDecoder.decodeSubject(response.accessToken).orEmpty()
        tokenStore.saveSession(parentId, response.accessToken, response.refreshToken)
    }.recoverCatching { throw errorMapper.toApiException(it) }

    override suspend fun logout(): Result<Unit> = runCatching {
        val refreshToken = tokenStore.getRefreshToken()
        if (refreshToken != null) {
            // Best-effort: even if the server call fails (offline, already-expired token),
            // still clear local state so the user is logged out on this device.
            runCatching { api.logout(LogoutRequest(refreshToken)) }
        }
        tokenStore.clear()
    }

    override fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()
}
