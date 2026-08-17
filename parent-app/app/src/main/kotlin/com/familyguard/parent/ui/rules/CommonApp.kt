package com.familyguard.parent.ui.rules

/**
 * Small hardcoded app picker for this slice's MVP scope — no full installed-app catalog
 * sync (per the product story). Package names are the real, current Play Store package
 * names for each app.
 */
enum class CommonApp(val packageName: String, val displayName: String) {
    YOUTUBE("com.google.android.youtube", "YouTube"),
    INSTAGRAM("com.instagram.android", "Instagram"),
    TIKTOK("com.zhiliaoapp.musically", "TikTok"),
    CHROME("com.android.chrome", "Chrome"),
}
