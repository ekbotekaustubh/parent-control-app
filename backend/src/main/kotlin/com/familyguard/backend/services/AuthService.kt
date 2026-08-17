package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.auth.PasswordHasher
import com.familyguard.backend.auth.TokenHasher
import com.familyguard.backend.db.tables.AuditActorType
import com.familyguard.backend.db.tables.ParentRefreshTokensTable
import com.familyguard.backend.db.tables.ParentsTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.AuthTokensResponse
import com.familyguard.shared.dto.LoginRequest
import com.familyguard.shared.dto.LogoutRequest
import com.familyguard.shared.dto.RefreshRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.SignupResponse
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Parent signup/login/refresh/logout. Refresh follows the standard rotate-and-detect-reuse
 * pattern (api-spec.md "POST /auth/refresh"): each use marks the presented token
 * `revoked_at` + `replaced_by_id`, and issues a fresh one. If an already-revoked token is
 * presented again, that's treated as a compromise signal - the entire chain (every
 * still-active refresh token for that parent) is revoked and an audit_log row is written.
 */
class AuthService(private val jwtConfig: JwtConfig) {

    // Not specified in api-spec.md; 30 days is this slice's own reasonable default for a
    // parent's refresh token lifetime (pairing-security.md only pins the device token's
    // ~180-day lifetime explicitly).
    private val refreshTokenTtlDays = 30L

    fun signup(request: SignupRequest): SignupResponse = transaction {
        val normalizedEmail = request.email.trim().lowercase()
        val existing = ParentsTable.selectAll().where { ParentsTable.email eq normalizedEmail }.singleOrNull()
        if (existing != null) {
            throw ApiException.Conflict("EMAIL_TAKEN", "An account with this email already exists.")
        }

        val parentId = try {
            ParentsTable.insertAndGetId {
                it[email] = normalizedEmail
                it[passwordHash] = PasswordHasher.hash(request.password)
                it[displayName] = request.displayName
            }.value
        } catch (e: ExposedSQLException) {
            // Race: two concurrent signups with the same email both pass the pre-check.
            throw ApiException.Conflict("EMAIL_TAKEN", "An account with this email already exists.")
        }

        val accessToken = jwtConfig.issueParentAccessToken(parentId)
        val refreshToken = issueParentRefreshToken(parentId)
        SignupResponse(
            parentId = parentId.toString(),
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtConfig.accessTokenTtlSeconds,
        )
    }

    fun login(request: LoginRequest): AuthTokensResponse = transaction {
        val normalizedEmail = request.email.trim().lowercase()
        val row = ParentsTable.selectAll().where { ParentsTable.email eq normalizedEmail }.singleOrNull()
            // Same error for "no such email" and "wrong password" - never leak which one.
            ?: throw ApiException.Unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect.")

        if (!PasswordHasher.verify(request.password, row[ParentsTable.passwordHash])) {
            throw ApiException.Unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect.")
        }

        val parentId = row[ParentsTable.id].value
        val accessToken = jwtConfig.issueParentAccessToken(parentId)
        val refreshToken = issueParentRefreshToken(parentId)
        AuthTokensResponse(accessToken, refreshToken, jwtConfig.accessTokenTtlSeconds)
    }

    /**
     * NOTE (deviation): api-spec.md's prose for `/auth/refresh` shows a response with no
     * `expiresIn` field, but `shared/` has no DTO for "accessToken + refreshToken only"
     * apart from the device-specific `DeviceTokenRefreshResponse`. Reusing a parent-specific
     * DTO without inventing a new one means using `AuthTokensResponse`, which does carry
     * `expiresIn` - an extra, harmless field rather than a shape mismatch.
     */
    fun refresh(request: RefreshRequest): AuthTokensResponse = transaction {
        val tokenHash = TokenHasher.sha256Hex(request.refreshToken)
        val row = ParentRefreshTokensTable.selectAll()
            .where { ParentRefreshTokensTable.tokenHash eq tokenHash }
            .singleOrNull()
            ?: throw ApiException.Unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid.")

        val parentId = row[ParentRefreshTokensTable.parentId].value
        val now = OffsetDateTime.now()

        if (row[ParentRefreshTokensTable.revokedAt] != null) {
            // Reuse of an already-rotated/revoked token - likely compromise. Revoke the
            // whole chain (every still-active token for this parent) and audit it.
            ParentRefreshTokensTable.update({
                (ParentRefreshTokensTable.parentId eq parentId) and (ParentRefreshTokensTable.revokedAt.isNull())
            }) {
                it[revokedAt] = now
            }
            AuditService.log(
                actorType = AuditActorType.PARENT,
                actorId = parentId,
                action = "auth.refresh_token_reused",
                targetType = "parent",
                targetId = parentId,
            )
            throw ApiException.Unauthorized(
                "REFRESH_TOKEN_REUSED",
                "This refresh token was already used. All sessions have been revoked; please log in again.",
            )
        }

        if (row[ParentRefreshTokensTable.expiresAt].isBefore(now)) {
            throw ApiException.Unauthorized("REFRESH_TOKEN_EXPIRED", "Refresh token has expired.")
        }

        val newTokenPlaintext = TokenHasher.randomOpaqueToken()
        val newTokenId = ParentRefreshTokensTable.insertAndGetId {
            it[ParentRefreshTokensTable.parentId] = parentId
            // Fully qualified: this scope also has a local `tokenHash` (the *presented*
            // token's hash, above) which would otherwise shadow the column reference here.
            it[ParentRefreshTokensTable.tokenHash] = TokenHasher.sha256Hex(newTokenPlaintext)
            it[expiresAt] = now.plusDays(refreshTokenTtlDays)
        }.value

        ParentRefreshTokensTable.update({ ParentRefreshTokensTable.id eq row[ParentRefreshTokensTable.id] }) {
            it[revokedAt] = now
            it[replacedById] = newTokenId
        }

        val accessToken = jwtConfig.issueParentAccessToken(parentId)
        AuthTokensResponse(accessToken, newTokenPlaintext, jwtConfig.accessTokenTtlSeconds)
    }

    /** Idempotent: revokes the token if found and not already revoked; always returns normally. */
    fun logout(request: LogoutRequest) {
        transaction {
            val tokenHash = TokenHasher.sha256Hex(request.refreshToken)
            val row = ParentRefreshTokensTable.selectAll()
                .where { ParentRefreshTokensTable.tokenHash eq tokenHash }
                .singleOrNull() ?: return@transaction

            if (row[ParentRefreshTokensTable.revokedAt] == null) {
                ParentRefreshTokensTable.update({ ParentRefreshTokensTable.id eq row[ParentRefreshTokensTable.id] }) {
                    it[revokedAt] = OffsetDateTime.now()
                }
            }
        }
    }

    private fun issueParentRefreshToken(parentId: UUID): String {
        val plaintext = TokenHasher.randomOpaqueToken()
        ParentRefreshTokensTable.insert {
            it[ParentRefreshTokensTable.parentId] = parentId
            it[tokenHash] = TokenHasher.sha256Hex(plaintext)
            it[expiresAt] = OffsetDateTime.now().plusDays(refreshTokenTtlDays)
        }
        return plaintext
    }
}
