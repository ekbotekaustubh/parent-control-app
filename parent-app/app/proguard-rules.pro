# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# Minification is disabled for both build types in this slice (see app/build.gradle.kts),
# so these rules aren't currently exercised — kept as a placeholder for when release builds
# turn isMinifyEnabled on.

# kotlinx.serialization: keep serializer() companions for shared/ DTOs reflectively looked
# up by the Json converter.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class com.familyguard.shared.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.familyguard.shared.enums.** {
    kotlinx.serialization.KSerializer serializer(...);
}
