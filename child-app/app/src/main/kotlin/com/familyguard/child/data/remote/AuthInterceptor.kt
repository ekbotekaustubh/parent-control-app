package com.familyguard.child.data.remote

import com.familyguard.child.data.local.TokenStore
import com.familyguard.shared.dto.DeviceTokenRefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Paths that must NOT get an Authorization header attached — see docs/api-spec.md: these
 * are either unauthenticated (protected by a claim ticket/refresh token in the body/header
 * instead) or are the token-refresh call itself. */
private val NO_AUTH_HEADER_PATHS = setOf(
    "pairing/claim",
    "devices/token/refresh",
)

private fun Request.matchesNoAuthPath(): Boolean {
    val path = url.encodedPath.removePrefix("/")
    return NO_AUTH_HEADER_PATHS.any { path.endsWith(it) } ||
        path.contains("/token-exchange") ||
        path.contains("/status")
}

/**
 * Attaches `Authorization: Bearer <deviceAccessToken>` to every authenticated device call.
 * Token refresh itself is handled by [DeviceTokenAuthenticator], not here — an
 * `Interceptor` only sees each request once and can't cleanly retry after a 401, which is
 * exactly what `okhttp3.Authenticator` exists for.
 */
class AuthHeaderInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.matchesNoAuthPath()) {
            return chain.proceed(original)
        }
        val token = tokenStore.accessToken ?: return chain.proceed(original)
        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}

/**
 * On a 401 from any device-authenticated call, performs the refresh-token rotation exactly
 * once (per docs/pairing-security.md's "Ongoing auth" section) and retries the original
 * request with the new access token. If the refresh call itself fails (e.g. the refresh
 * token was already used/revoked — the reuse-detection case), the whole device session is
 * treated as dead: tokens are cleared locally so the app falls back to the pairing flow,
 * matching the backend's "no self-service path back to approved" design.
 *
 * Takes a [Provider] of [ChildApiService] rather than [ChildApiService] itself. There is
 * only one `OkHttpClient`/`Retrofit`/`ChildApiService` in this app (see
 * `di/NetworkModule.kt`) — the refresh call reuses it, since `refreshDeviceToken` is on
 * [NO_AUTH_HEADER_PATHS] and so never re-enters this same `Authenticator`. The `Provider`
 * indirection exists purely to satisfy Dagger's compile-time dependency graph: this
 * `Authenticator` is itself a dependency of `OkHttpClient`, and `ChildApiService` depends on
 * that same `OkHttpClient`, so injecting `ChildApiService` directly here would be circular.
 * `Provider<T>` defers resolution until [authenticate] actually calls `.get()` — by then the
 * `OkHttpClient` singleton is already fully constructed, so the call resolves cleanly.
 */
@Singleton
class DeviceTokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApiProvider: Provider<ChildApiService>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Only ever attempt one refresh per request chain — avoids infinite retry loops.
        if (responseCount(response) >= 2) return null
        if (response.request.matchesNoAuthPath()) return null

        val refreshToken = tokenStore.refreshToken ?: return null

        val newTokens = try {
            runBlocking {
                val result = refreshApiProvider.get().refreshDeviceToken(DeviceTokenRefreshRequest(refreshToken))
                if (result.isSuccessful) result.body() else null
            }
        } catch (e: Exception) {
            null
        }

        if (newTokens == null) {
            // Refresh failed — likely reuse-detection revocation. Clear local credentials so
            // the app routes back to the pairing flow rather than looping on 401s.
            tokenStore.clearAll()
            return null
        }

        tokenStore.accessToken = newTokens.accessToken
        tokenStore.refreshToken = newTokens.refreshToken

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
