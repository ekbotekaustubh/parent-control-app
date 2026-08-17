package com.familyguard.backend.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * SHA-256 hex hashing for opaque secrets at rest (pairing codes, claim tickets, refresh
 * tokens) plus a generator for the opaque plaintext values themselves. Only the hash is
 * ever persisted; the plaintext is returned to the client exactly once, at issuance.
 */
object TokenHasher {
    private val secureRandom = SecureRandom()

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (b in digest) {
            hex.append(String.format("%02x", b))
        }
        return hex.toString()
    }

    /** URL-safe, unpadded base64 opaque token (e.g. for refresh tokens and claim tickets). */
    fun randomOpaqueToken(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
