package com.familyguard.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

/**
 * Owns the Hikari connection pool, runs Flyway migrations, and wires Exposed to the pool.
 * `init(ApplicationConfig)` is used by the running server; `init(jdbcUrl, user, password)`
 * is used directly by integration tests pointing at a Testcontainers Postgres instance.
 */
object DatabaseFactory {
    lateinit var dataSource: DataSource
        private set

    fun init(config: ApplicationConfig) {
        val jdbcUrl = config.propertyOrNull("database.jdbcUrl")?.getString()
            ?: "jdbc:postgresql://localhost:5432/familyguard"
        val user = config.propertyOrNull("database.user")?.getString() ?: "familyguard"
        val password = config.propertyOrNull("database.password")?.getString() ?: "familyguard"
        init(jdbcUrl, user, password)
    }

    fun init(jdbcUrl: String, user: String, password: String) {
        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
        }
        val ds = HikariDataSource(hikariConfig)
        dataSource = ds

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        Database.connect(ds)
    }
}
