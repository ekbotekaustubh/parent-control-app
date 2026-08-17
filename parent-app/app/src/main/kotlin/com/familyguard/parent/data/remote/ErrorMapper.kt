package com.familyguard.parent.data.remote

import com.familyguard.shared.dto.ErrorResponse
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Translates raw exceptions from Retrofit calls into a friendly [ApiException] using the
 * backend's uniform error envelope (docs/api-spec.md: `{ "error": { "code", "message" } }`).
 * Every repository funnels its failures through this so ViewModels only ever see a message
 * that's safe to show directly in an Error UI state.
 */
@Singleton
class ErrorMapper @Inject constructor(private val json: Json) {

    fun toApiException(throwable: Throwable): ApiException {
        if (throwable is ApiException) return throwable
        return when (throwable) {
            is HttpException -> fromHttpException(throwable)
            is IOException -> ApiException("Can't reach the server. Check your connection and try again.")
            else -> ApiException("Something went wrong. Please try again.")
        }
    }

    private fun fromHttpException(exception: HttpException): ApiException {
        val rawBody = exception.response()?.errorBody()?.string()
        val parsed = rawBody?.let {
            runCatching { json.decodeFromString(ErrorResponse.serializer(), it) }.getOrNull()
        }
        val message = parsed?.error?.message
            ?: "Something went wrong (${exception.code()}). Please try again."
        return ApiException(message, parsed?.error?.code)
    }
}
