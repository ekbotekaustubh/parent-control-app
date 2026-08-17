package com.familyguard.child.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small, non-sensitive metadata (last successful usage-sync timestamp) shown on
 * `ui/home/HomeScreen.kt`. Deliberately plain `SharedPreferences`, NOT
 * `EncryptedSharedPreferences` — encryption is reserved for actual credentials
 * (`TokenStore`); a timestamp carries no confidentiality requirement, and using the plain
 * API here keeps that distinction visible in the code rather than blurring "this file is
 * for secrets" into "this file is for any small key-value data".
 */
@Singleton
class SyncMetadataStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    var lastUsageSyncAtMillis: Long
        get() = prefs.getLong(KEY_LAST_USAGE_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_USAGE_SYNC_AT, value).apply()

    companion object {
        private const val PREFS_FILE_NAME = "child_sync_metadata"
        private const val KEY_LAST_USAGE_SYNC_AT = "last_usage_sync_at"
    }
}
