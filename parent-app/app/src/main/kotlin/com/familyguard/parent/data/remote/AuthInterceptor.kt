package com.familyguard.parent.data.remote

import com.familyguard.parent.data.local.TokenStore
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <accessToken>` to every request except the handful of
 * auth endpoints that either don't require it or are called before a token exists
 * (signup/login/refresh). Skipping those by path avoids sending a stale/absent token where
 * it's meaningless and keeps this interceptor stateless.
 */
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        if (NO_AUTH_HEADER_SUFFIXES.any { path.endsWith(it) }) {
            return chain.proceed(request)
        }

        val accessToken = tokenStore.getAccessToken()
        val authorizedRequest = if (accessToken != null) {
            request.newBuilder().header("Authorization", "Bearer $accessToken").build()
        } else {
            request
        }
        return chain.proceed(authorizedRequest)
    }

    private companion object {
        val NO_AUTH_HEADER_SUFFIXES = listOf("auth/signup", "auth/login", "auth/refresh")
    }
}
