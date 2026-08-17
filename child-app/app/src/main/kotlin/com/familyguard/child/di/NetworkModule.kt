package com.familyguard.child.di

import com.familyguard.child.BuildConfig
import com.familyguard.child.data.remote.AuthHeaderInterceptor
import com.familyguard.child.data.remote.ChildApiService
import com.familyguard.child.data.remote.DeviceTokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authHeaderInterceptor: AuthHeaderInterceptor,
        // Provider<ChildApiService>, not ChildApiService directly, deliberately: this
        // Authenticator is itself a dependency of this OkHttpClient (below), and
        // ChildApiService's Retrofit instance depends on this same OkHttpClient. A plain
        // constructor-injected ChildApiService would create a circular dependency; wrapping
        // it in Dagger's Provider<T> defers resolution until the Authenticator actually
        // needs to make the one-off refresh call, breaking the cycle at graph-construction
        // time. See data/remote/AuthInterceptor.kt's KDoc for the full rationale.
        authenticator: DeviceTokenAuthenticator,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BODY logging would risk leaking tokens/PII into logcat in a debug build;
            // BASIC (method/URL/status only) is safe in all build types.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authHeaderInterceptor)
            .authenticator(authenticator)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideChildApiService(retrofit: Retrofit): ChildApiService =
        retrofit.create(ChildApiService::class.java)
}
