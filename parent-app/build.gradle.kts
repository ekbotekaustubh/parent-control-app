// Root build script for the Parent App composite build. Individual module build scripts
// (app/build.gradle.kts) declare their own plugin blocks; this file only declares plugin
// versions once at the root so subprojects can `apply false` / `alias` them without
// re-resolving versions per module.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android.gradle) apply false
}
