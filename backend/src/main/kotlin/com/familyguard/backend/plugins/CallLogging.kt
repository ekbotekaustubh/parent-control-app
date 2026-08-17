package com.familyguard.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.event.Level

/**
 * Logs method/path/status/duration only - NEVER request or response bodies, since those
 * carry passwords, pairing codes, claim tickets, and refresh tokens.
 */
fun Application.configureCallLogging() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val method = call.request.httpMethod.value
            val path = call.request.path()
            "$method $path -> $status"
        }
    }
}
