pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "parent-app"

// Composite build: ../shared is a plain Kotlin/JVM module containing the DTOs, enums, and
// API path constants shared with backend/ and child-app/. See shared/build.gradle.kts for
// the group/version ("com.familyguard.shared:shared:1.0") that app/build.gradle.kts depends
// on via includeBuild substitution.
includeBuild("../shared")

include(":app")
