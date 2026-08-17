package com.familyguard.backend

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.plugins.configureCallLogging
import com.familyguard.backend.plugins.configureRateLimiting
import com.familyguard.backend.plugins.configureRouting
import com.familyguard.backend.plugins.configureSecurity
import com.familyguard.backend.plugins.configureSerialization
import com.familyguard.backend.plugins.configureStatusPages
import com.typesafe.config.ConfigFactory
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = HoconApplicationConfig(ConfigFactory.load())
    val port = config.propertyOrNull("ktor.deployment.port")?.getString()?.toInt() ?: 8080

    DatabaseFactory.init(config)

    embeddedServer(Netty, port = port, module = { module(config) }).start(wait = true)
}

fun Application.module(config: ApplicationConfig) {
    val jwtSecret = config.propertyOrNull("jwt.secret")?.getString()
        ?: "dev-only-insecure-secret-change-me-0123456789abcdef"
    val jwtIssuer = config.propertyOrNull("jwt.issuer")?.getString() ?: "familyguard-backend"
    val accessTokenTtlSeconds = config.propertyOrNull("jwt.accessTokenTtlSeconds")?.getString()?.toLong() ?: 1200L
    val jwtConfig = JwtConfig(jwtSecret, jwtIssuer, accessTokenTtlSeconds)

    configureSerialization()
    configureSecurity(jwtConfig)
    configureStatusPages()
    configureCallLogging()
    configureRateLimiting()
    configureRouting(jwtConfig)
}
