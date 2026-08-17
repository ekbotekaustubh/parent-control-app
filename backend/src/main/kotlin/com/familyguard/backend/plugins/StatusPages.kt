package com.familyguard.backend.plugins

import com.familyguard.shared.dto.ErrorBody
import com.familyguard.shared.dto.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

/**
 * Uniform API error hierarchy. Note there is deliberately NO Forbidden(403) variant: per
 * api-spec.md, every ownership check that fails (a real resource that just isn't the
 * caller's) must respond 404, not 403 — a 403 would confirm the id exists under someone
 * else's account. Everywhere the rest of the codebase would be tempted to "throw Forbidden",
 * it throws NotFound instead.
 */
sealed class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message) {

    class BadRequest(code: String = "BAD_REQUEST", message: String = "The request was invalid.") :
        ApiException(HttpStatusCode.BadRequest, code, message)

    class Unauthorized(code: String = "UNAUTHORIZED", message: String = "Authentication required.") :
        ApiException(HttpStatusCode.Unauthorized, code, message)

    /** Also used for the "exists but not owned by caller" case - see class doc above. */
    class NotFound(code: String = "NOT_FOUND", message: String = "Resource not found.") :
        ApiException(HttpStatusCode.NotFound, code, message)

    class Conflict(code: String, message: String) :
        ApiException(HttpStatusCode.Conflict, code, message)

    class Gone(code: String, message: String) :
        ApiException(HttpStatusCode.Gone, code, message)

    class RateLimited(code: String = "RATE_LIMITED", message: String = "Too many requests. Try again later.") :
        ApiException(HttpStatusCode.TooManyRequests, code, message)
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(ErrorBody(cause.code, cause.message)))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception handling ${call.request.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorBody("INTERNAL_ERROR", "An unexpected error occurred.")),
            )
        }
    }
}
