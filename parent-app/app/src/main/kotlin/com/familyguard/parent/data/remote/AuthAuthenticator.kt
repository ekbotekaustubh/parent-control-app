package com.familyguard.parent.data.remote

import com.familyguard.parent.data.local.TokenStore
import com.familyguard.parent.di.RefreshApi
import com.familyguard.shared.dto.RefreshRequest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Implements the "401 -> refresh-once-and-retry" flow via `okhttp3.Authenticator`, which
 * OkHttp always invokes off the main thread — `runBlocking` here is safe and is the
 * standard way to call a suspend Retrofit function from this callback.
 *
 * [refreshApi] is a *separate*, non-authenticated Retrofit/OkHttp instance (see
 * di/NetworkModule.kt's `@RefreshApi` binding) that has neither this authenticator nor
 * [AuthInterceptor] attached. Reusing the main authenticated client here would recurse:
 * a failed refresh call would itself 401 and re-trigger this same authenticator.
 */
class AuthAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    @RefreshApi private val refreshApi: ParentApiService,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val failedPath = response.request.url.encodedPath
        if (NO_RETRY_SUFFIXES.any { failedPath.endsWith(it) }) return null
        if (responseChainLength(response) > 2) return null // already retried once; give up

        val refreshToken = tokenStore.getRefreshToken() ?: return null
        val failedAccessToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        return runBlocking {
            refreshMutex.withLock {
                // Another request may have already refreshed while we waited for the lock.
                val currentAccessToken = tokenStore.getAccessToken()
                if (currentAccessToken != null && currentAccessToken != failedAccessToken) {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $currentAccessToken")
                        .build()
                }

                runCatching { refreshApi.refreshParentToken(RefreshRequest(refreshToken)) }
                    .onSuccess { tokens ->
                        tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken)
                    }
                    .fold(
                        onSuccess = { tokens ->
                            response.request.newBuilder()
                                .header("Authorization", "Bearer ${tokens.accessToken}")
                                .build()
                        },
                        onFailure = {
                            // Refresh token is dead (expired, revoked, or reuse-detected server
                            // side per docs/pairing-security.md's rotation model) — force a
                            // logout rather than looping; AuthViewModel/nav observes this via
                            // TokenStore.isLoggedIn() on next screen interaction.
                            tokenStore.clear()
                            null
                        },
                    )
            }
        }
    }

    private fun responseChainLength(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        val NO_RETRY_SUFFIXES = listOf("auth/signup", "auth/login", "auth/refresh")
    }
}
