package com.familyguard.child.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONLY place device credentials (deviceId, access/refresh tokens, claim ticket) are
 * persisted. Backed by [EncryptedSharedPreferences] (AES256-GCM values, AES256-SIV keys),
 * never Room (unencrypted SQLite) and never plain `SharedPreferences`. Values here are
 * never logged — see `data/remote/AuthInterceptor.kt` for the only other place they're
 * read, and only to set the `Authorization` header.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    /** Transient — only held between claim and successful token-exchange. */
    var claimTicket: String?
        get() = prefs.getString(KEY_CLAIM_TICKET, null)
        set(value) = prefs.edit().putString(KEY_CLAIM_TICKET, value).apply()

    fun isPaired(): Boolean = deviceId != null && accessToken != null && refreshToken != null

    /** Clears everything — used when a device is revoked/reuse-detected and must re-pair from scratch. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "child_secure_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_CLAIM_TICKET = "claim_ticket"
    }
}
