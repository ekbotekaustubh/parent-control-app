package com.familyguard.child.data.repository

import android.os.Build
import com.familyguard.child.BuildConfig
import com.familyguard.child.data.local.TokenStore
import com.familyguard.child.data.remote.ChildApiService
import com.familyguard.shared.dto.ClaimRequest
import com.familyguard.shared.dto.TokenExchangeRequest
import com.familyguard.shared.enums.DeviceStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the device side of the pairing flow described in
 * `docs/pairing-security.md`: claim the 8-char code, poll approval status, then exchange
 * the claim ticket for real device tokens.
 */
@Singleton
class PairingRepository @Inject constructor(
    private val api: ChildApiService,
    private val tokenStore: TokenStore,
) {

    /**
     * Extracts the 8-char code from either a raw manual-entry string or a scanned
     * `familyguard://pair?code=XXXXXXXX` URI (see docs/pairing-security.md's QR format).
     */
    fun extractCode(scannedOrTypedValue: String): String? {
        val trimmed = scannedOrTypedValue.trim()
        val uriMatch = Regex("familyguard://pair\\?code=([A-Za-z0-9]{8})").find(trimmed)
        if (uriMatch != null) return uriMatch.groupValues[1].uppercase()
        val manual = trimmed.uppercase()
        return if (manual.length == 8) manual else null
    }

    suspend fun claim(code: String): Result<String> = runCatching {
        val response = api.claimPairingCode(
            ClaimRequest(
                code = code,
                deviceModel = Build.MODEL ?: "unknown",
                osVersion = Build.VERSION.RELEASE ?: "unknown",
                appVersion = BuildConfig.VERSION_NAME,
            ),
        )
        tokenStore.deviceId = response.deviceId
        tokenStore.claimTicket = response.claimTicket
        response.deviceId
    }

    suspend fun pollStatus(): Result<DeviceStatus> = runCatching {
        val deviceId = tokenStore.deviceId ?: error("No pending claim — call claim() first.")
        val claimTicket = tokenStore.claimTicket ?: error("No claim ticket — call claim() first.")
        api.getClaimStatus(deviceId, claimTicket).status
    }

    /** Called once [pollStatus] reports APPROVED. */
    suspend fun exchangeToken(): Result<Unit> = runCatching {
        val deviceId = tokenStore.deviceId ?: error("No pending claim — call claim() first.")
        val claimTicket = tokenStore.claimTicket ?: error("No claim ticket — call claim() first.")
        val tokens = api.exchangeToken(deviceId, TokenExchangeRequest(claimTicket))
        tokenStore.accessToken = tokens.deviceAccessToken
        tokenStore.refreshToken = tokens.deviceRefreshToken
        // Claim ticket is single-use server-side; drop the local copy too.
        tokenStore.claimTicket = null
    }

    fun isPaired(): Boolean = tokenStore.isPaired()
}
