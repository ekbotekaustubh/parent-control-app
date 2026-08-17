package com.familyguard.parent.di

import com.familyguard.parent.BuildConfig
import com.familyguard.parent.data.remote.AuthAuthenticator
import com.familyguard.parent.data.remote.AuthInterceptor
import com.familyguard.parent.data.remote.ParentApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The full, authenticated client used by the rest of the app (attaches tokens, retries on 401). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedApi

/**
 * A plain client with NO [AuthInterceptor] / [AuthAuthenticator] attached, used only inside
 * [AuthAuthenticator] itself to call `/auth/refresh` without recursing back into the 401
 * handler that triggered it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshApi

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        // BODY logging is fine for this vertical slice's development builds — tokens live
        // only in headers stripped from these logs' default formatting is NOT guaranteed,
        // so this is intentionally HEADERS-level, never BODY, to avoid ever printing a
        // refresh/access token to logcat.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
    }

    @Provides
    @Singleton
    fun provideBaseOkHttpClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

    // --- Refresh-only stack (no auth interceptor/authenticator; breaks the DI/recursion cycle) ---

    @Provides
    @Singleton
    @RefreshApi
    fun provideRefreshRetrofit(baseClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(baseClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @RefreshApi
    fun provideRefreshApiService(@RefreshApi retrofit: Retrofit): ParentApiService =
        retrofit.create(ParentApiService::class.java)

    // --- Full authenticated stack ---
    // AuthAuthenticator itself is NOT provided here: it has an `@Inject constructor` (see
    // data/remote/AuthAuthenticator.kt) whose `@RefreshApi ParentApiService` parameter is
    // satisfied by provideRefreshApiService() above, so Hilt constructs it directly. A
    // second explicit @Provides for the same type here would be a duplicate binding.

    @Provides
    @Singleton
    fun provideAuthenticatedOkHttpClient(
        baseClient: OkHttpClient,
        authInterceptor: AuthInterceptor,
        authAuthenticator: AuthAuthenticator,
    ): OkHttpClient =
        baseClient.newBuilder()
            .addInterceptor(authInterceptor)
            .authenticator(authAuthenticator)
            .build()

    @Provides
    @Singleton
    @AuthenticatedApi
    fun provideAuthenticatedRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideParentApiService(@AuthenticatedApi retrofit: Retrofit): ParentApiService =
        retrofit.create(ParentApiService::class.java)
}
