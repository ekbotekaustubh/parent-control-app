package com.familyguard.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimiter
import kotlin.time.Duration.Companion.minutes

/**
 * Applied to /auth/* and /pairing/claim per api-spec.md ("Rate limiting applies to
 * /auth/* and /pairing/claim"). Limits are this slice's own reasonable defaults - the docs
 * don't pin exact numbers, only which routes must be limited. Keyed by remote IP (Ktor's
 * default key extractor), consistent with pairing-security.md's "rate-limited per IP to
 * blunt brute-forcing the 8-char code space".
 */
val AUTH_RATE_LIMIT = RateLimitName("auth")
val PAIRING_CLAIM_RATE_LIMIT = RateLimitName("pairing-claim")

fun Application.configureRateLimiting() {
    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
        }
        register(PAIRING_CLAIM_RATE_LIMIT) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
        }
    }
}
