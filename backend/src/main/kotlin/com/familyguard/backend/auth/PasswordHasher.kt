package com.familyguard.backend.auth

import org.mindrot.jbcrypt.BCrypt

/** BCrypt wrapper. Passwords are never stored, logged, or returned in plaintext anywhere. */
object PasswordHasher {
    private const val LOG_ROUNDS = 12

    fun hash(plaintext: String): String = BCrypt.hashpw(plaintext, BCrypt.gensalt(LOG_ROUNDS))

    fun verify(plaintext: String, hash: String): Boolean =
        try {
            BCrypt.checkpw(plaintext, hash)
        } catch (e: IllegalArgumentException) {
            // Malformed stored hash - fail closed rather than throw.
            false
        }
}
