plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.familyguard.backend"
version = "1.0"

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.familyguard.backend.ApplicationKt")
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.familyguard.shared:shared")

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.rate.limit)

    // DB
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)

    // Auth
    implementation(libs.java.jwt)
    implementation(libs.jbcrypt)

    // HOCON application.conf parsing (Application.kt uses ConfigFactory/HoconApplicationConfig directly)
    implementation(libs.typesafe.config)

    // Logging
    runtimeOnly(libs.logback.classic)

    // Unit tests
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)

    // Integration tests additionally get Testcontainers; see sourceSets block below for
    // how integrationTestImplementation extends testImplementation.
    "integrationTestImplementation"(libs.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.testcontainers.junit.jupiter)
}

sourceSets {
    create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
configurations.getByName("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.test {
    useJUnitPlatform()
}

// Separate from `test` on purpose: integration tests spin up real Postgres via
// Testcontainers (needs Docker), so they must not run as part of a plain `./gradlew test`.
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against a real Postgres via Testcontainers (requires Docker)."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}
