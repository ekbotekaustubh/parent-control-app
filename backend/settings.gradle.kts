rootProject.name = "backend"

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

// Composite build: shared/ declares group="com.familyguard.shared", name="shared" (its
// rootProject.name), so backend/build.gradle.kts depends on it as
// "com.familyguard.shared:shared" and Gradle substitutes that with this included build's
// project automatically (no manual dependencySubstitution block needed).
includeBuild("../shared")
