package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.auth.TokenHasher
import com.familyguard.backend.db.tables.AuditActorType
import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.db.tables.DeviceRefreshTokensTable
import com.familyguard.backend.db.tables.DeviceStatusValues
import com.familyguard.backend.db.tables.DevicesTable
import com.familyguard.backend.db.tables.PairingSessionStatusValues
import com.familyguard.backend.db.tables.PairingSessionsTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.ClaimRequest
import com.familyguard.shared.dto.ClaimResponse
import com.familyguard.shared.dto.ClaimStatusResponse
import com.familyguard.shared.dto.DeviceResponse
import com.familyguard.shared.dto.DeviceTokenRefreshRequest
import com.familyguard.shared.dto.DeviceTokenRefreshResponse
import com.familyguard.shared.dto.DeviceTokensResponse
import com.familyguard.shared.dto.PairingCodeResponse
import com.familyguard.shared.enums.DeviceStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The highest-risk logic in the backend: the pairing-code and claim-ticket state machines
 * from pairing-security.md, plus ongoing device-token lifecycle (refresh/revoke).
 */
class PairingService(private val jwtConfig: JwtConfig) {

    private val deviceRefreshTokenTtlDays = 180L

    fun generateCode(parentId: UUID, childId: UUID): PairingCodeResponse = transaction {
        requireOwnedChild(parentId, childId)

        // Invalidate any existing pending session for this child. The partial unique index
        // (child_id) WHERE status='pending' enforces this at the DB level too, but we do it
        // explicitly here so the subsequent INSERT of a new pending row doesn't just fail.
        PairingSessionsTable.update({
            (PairingSessionsTable.childId eq childId) and (PairingSessionsTable.status eq PairingSessionStatusValues.PENDING)
        }) {
            it[status] = PairingSessionStatusValues.REVOKED
        }

        val code = generatePairingCode()
        val expiresAt = OffsetDateTime.now().plusMinutes(10)
        PairingSessionsTable.insert {
            it[PairingSessionsTable.childId] = childId
            it[codeHash] = TokenHasher.sha256Hex(code)
            it[status] = PairingSessionStatusValues.PENDING
            it[PairingSessionsTable.expiresAt] = expiresAt
        }

        AuditService.log(AuditActorType.PARENT, parentId, "pairing.code_generated", "child", childId)

        PairingCodeResponse(code, expiresAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }

    /**
     * The single most security-critical operation in the backend. The pending -> claimed
     * transition is one atomic conditional UPDATE ... WHERE status='pending' AND
     * expires_at > now() RETURNING child_id, executed as literal SQL inside this
     * transaction - not a SELECT then UPDATE - so that under a concurrent-claim race only
     * one caller's UPDATE can ever see status='pending' and win. `deviceId` is generated
     * client-side *before* the UPDATE so it can be written into claimed_by_device_id in the
     * same statement, without a chicken-and-egg dependency on the devices row existing yet.
     */
    fun claim(request: ClaimRequest): ClaimResponse = transaction {
        val normalizedCode = request.code.trim().uppercase()
        val codeHash = TokenHasher.sha256Hex(normalizedCode)
        val deviceId = UUID.randomUUID()

        val childId = attemptAtomicClaim(codeHash, deviceId) ?: run {
            val session = PairingSessionsTable.selectAll()
                .where { PairingSessionsTable.codeHash eq codeHash }
                .singleOrNull()
                ?: throw ApiException.NotFound("CODE_NOT_FOUND", "Invalid pairing code.")

            when (session[PairingSessionsTable.status]) {
                PairingSessionStatusValues.CLAIMED ->
                    throw ApiException.Conflict("CODE_ALREADY_CLAIMED", "This code has already been used.")
                // Still 'pending' in the row but our atomic UPDATE's WHERE excluded it, so
                // the only remaining explanation is expires_at <= now().
                PairingSessionStatusValues.PENDING ->
                    throw ApiException.Gone("CODE_EXPIRED", "This code has expired.")
                else ->
                    throw ApiException.NotFound("CODE_NOT_FOUND", "Invalid pairing code.")
            }
        }

        val claimTicket = TokenHasher.randomOpaqueToken()
        DevicesTable.insert {
            it[id] = deviceId
            it[DevicesTable.childId] = childId
            it[model] = request.deviceModel
            it[osVersion] = request.osVersion
            it[appVersion] = request.appVersion
            it[status] = DeviceStatusValues.PENDING_APPROVAL
            it[pendingClaimTicketHash] = TokenHasher.sha256Hex(claimTicket)
            it[claimTicketExpiresAt] = OffsetDateTime.now().plusMinutes(15)
            it[pairedAt] = OffsetDateTime.now()
        }

        AuditService.log(AuditActorType.DEVICE, deviceId, "pairing.code_claimed", "device", deviceId)

        ClaimResponse(deviceId.toString(), claimTicket, 900L)
    }

    fun pollStatus(deviceId: UUID, claimTicket: String): ClaimStatusResponse = transaction {
        val row = requireValidClaimTicket(deviceId, claimTicket)
        ClaimStatusResponse(status = mapDeviceStatus(row[DevicesTable.status]))
    }

    /**
     * The only path that can flip a device to 'approved' - gated entirely by parent-JWT
     * ownership (child_id resolves to a child owned by the calling parent), never by
     * anything the device itself sends.
     */
    fun approve(parentId: UUID, deviceId: UUID): DeviceResponse = transaction {
        deviceOwnedByParent(deviceId, parentId)
            ?: throw ApiException.NotFound("DEVICE_NOT_FOUND", "No device with that id.")

        val current = DevicesTable.selectAll().where { DevicesTable.id eq deviceId }.single()
        if (current[DevicesTable.status] != DeviceStatusValues.PENDING_APPROVAL) {
            throw ApiException.Conflict("DEVICE_NOT_PENDING", "Device is not awaiting approval.")
        }

        DevicesTable.update({ DevicesTable.id eq deviceId }) {
            it[status] = DeviceStatusValues.APPROVED
            it[approvedAt] = OffsetDateTime.now()
        }

        AuditService.log(AuditActorType.PARENT, parentId, "device.approved", "device", deviceId)

        DevicesTable.selectAll().where { DevicesTable.id eq deviceId }.single().toDeviceResponse()
    }

    fun tokenExchange(deviceId: UUID, claimTicket: String): DeviceTokensResponse = transaction {
        val row = requireValidClaimTicket(deviceId, claimTicket)

        // Single-use: invalidate the ticket now that it's been matched, regardless of the
        // outcome of the status check below (pairing-security.md step 5).
        DevicesTable.update({ DevicesTable.id eq deviceId }) {
            it[pendingClaimTicketHash] = null
            it[claimTicketExpiresAt] = null
        }

        if (row[DevicesTable.status] != DeviceStatusValues.APPROVED) {
            throw ApiException.Conflict("DEVICE_NOT_APPROVED", "Device has not been approved by the parent yet.")
        }

        val childId = row[DevicesTable.childId].value
        val accessToken = jwtConfig.issueDeviceAccessToken(deviceId, childId)
        val refreshToken = issueDeviceRefreshToken(deviceId)

        AuditService.log(AuditActorType.DEVICE, deviceId, "device.token_exchanged", "device", deviceId)

        DeviceTokensResponse(accessToken, refreshToken, jwtConfig.accessTokenTtlSeconds)
    }

    /**
     * Same rotate-and-detect-reuse pattern as AuthService.refresh. Reuse additionally flips
     * the device to 'revoked' (pairing-security.md: "revoke the entire device session and
     * require re-pairing - do not attempt silent recovery").
     */
    fun refreshDeviceToken(request: DeviceTokenRefreshRequest): DeviceTokenRefreshResponse = transaction {
        val tokenHash = TokenHasher.sha256Hex(request.refreshToken)
        val row = DeviceRefreshTokensTable.selectAll()
            .where { DeviceRefreshTokensTable.tokenHash eq tokenHash }
            .singleOrNull()
            ?: throw ApiException.Unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid.")

        val deviceId = row[DeviceRefreshTokensTable.deviceId].value
        val now = OffsetDateTime.now()

        if (row[DeviceRefreshTokensTable.revokedAt] != null) {
            DeviceRefreshTokensTable.update({
                (DeviceRefreshTokensTable.deviceId eq deviceId) and (DeviceRefreshTokensTable.revokedAt.isNull())
            }) {
                it[revokedAt] = now
            }
            DevicesTable.update({ DevicesTable.id eq deviceId }) { it[status] = DeviceStatusValues.REVOKED }
            AuditService.log(
                AuditActorType.DEVICE,
                deviceId,
                "auth.device_refresh_token_reused",
                "device",
                deviceId,
            )
            throw ApiException.Unauthorized(
                "REFRESH_TOKEN_REUSED",
                "This refresh token was already used. Re-pairing is required.",
            )
        }

        if (row[DeviceRefreshTokensTable.expiresAt].isBefore(now)) {
            throw ApiException.Unauthorized("REFRESH_TOKEN_EXPIRED", "Refresh token has expired.")
        }

        val deviceRow = DevicesTable.selectAll().where { DevicesTable.id eq deviceId }.single()
        if (deviceRow[DevicesTable.status] == DeviceStatusValues.REVOKED) {
            throw ApiException.Unauthorized("DEVICE_REVOKED", "This device has been revoked. Re-pairing is required.")
        }
        val childId = deviceRow[DevicesTable.childId].value

        val newPlaintext = TokenHasher.randomOpaqueToken()
        val newId = DeviceRefreshTokensTable.insertAndGetId {
            it[DeviceRefreshTokensTable.deviceId] = deviceId
            it[DeviceRefreshTokensTable.tokenHash] = TokenHasher.sha256Hex(newPlaintext)
            it[expiresAt] = now.plusDays(deviceRefreshTokenTtlDays)
        }.value

        DeviceRefreshTokensTable.update({ DeviceRefreshTokensTable.id eq row[DeviceRefreshTokensTable.id] }) {
            it[revokedAt] = now
            it[replacedById] = newId
        }

        val accessToken = jwtConfig.issueDeviceAccessToken(deviceId, childId)
        DeviceTokenRefreshResponse(accessToken, newPlaintext)
    }

    fun listDevices(parentId: UUID, childId: UUID): List<DeviceResponse> = transaction {
        requireOwnedChild(parentId, childId)
        DevicesTable.selectAll().where { DevicesTable.childId eq childId }.map { it.toDeviceResponse() }
    }

    /** Revokes every refresh token for the device and flips status='revoked'. No self-service path back. */
    fun revoke(parentId: UUID, deviceId: UUID) {
        transaction {
            deviceOwnedByParent(deviceId, parentId)
                ?: throw ApiException.NotFound("DEVICE_NOT_FOUND", "No device with that id.")

            DeviceRefreshTokensTable.update({
                (DeviceRefreshTokensTable.deviceId eq deviceId) and (DeviceRefreshTokensTable.revokedAt.isNull())
            }) {
                it[revokedAt] = OffsetDateTime.now()
            }
            DevicesTable.update({ DevicesTable.id eq deviceId }) { it[status] = DeviceStatusValues.REVOKED }

            AuditService.log(AuditActorType.PARENT, parentId, "device.revoked", "device", deviceId)
        }
    }

    // ---- internal helpers ----

    private fun requireOwnedChild(parentId: UUID, childId: UUID) {
        val owns = ChildrenTable.selectAll()
            .where { (ChildrenTable.id eq childId) and (ChildrenTable.parentId eq parentId) }
            .count() > 0
        if (!owns) throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")
    }

    private fun deviceOwnedByParent(deviceId: UUID, parentId: UUID): ResultRow? =
        (DevicesTable innerJoin ChildrenTable)
            .selectAll()
            .where { (DevicesTable.id eq deviceId) and (ChildrenTable.parentId eq parentId) }
            .singleOrNull()

    private fun requireValidClaimTicket(deviceId: UUID, claimTicket: String): ResultRow {
        val row = DevicesTable.selectAll().where { DevicesTable.id eq deviceId }.singleOrNull()
            ?: throw ApiException.Unauthorized("INVALID_CLAIM_TICKET", "Invalid or expired claim ticket.")

        val storedHash = row[DevicesTable.pendingClaimTicketHash]
        val expiresAt = row[DevicesTable.claimTicketExpiresAt]
        val presentedHash = TokenHasher.sha256Hex(claimTicket)

        if (storedHash == null || storedHash != presentedHash || expiresAt == null || expiresAt.isBefore(OffsetDateTime.now())) {
            throw ApiException.Unauthorized("INVALID_CLAIM_TICKET", "Invalid or expired claim ticket.")
        }
        return row
    }

    private fun issueDeviceRefreshToken(deviceId: UUID): String {
        val plaintext = TokenHasher.randomOpaqueToken()
        DeviceRefreshTokensTable.insert {
            it[DeviceRefreshTokensTable.deviceId] = deviceId
            it[tokenHash] = TokenHasher.sha256Hex(plaintext)
            it[expiresAt] = OffsetDateTime.now().plusDays(deviceRefreshTokenTtlDays)
        }
        return plaintext
    }

    /**
     * The atomic claim itself, as literal SQL executed within the current transaction - see
     * the fun-level doc on `claim()` for why this must not be a SELECT-then-UPDATE.
     * `explicitStatementType = StatementType.SELECT` forces Exposed down the
     * result-set-returning execution path, which is required for `UPDATE ... RETURNING` to
     * hand back rows via JDBC.
     */
    private fun Transaction.attemptAtomicClaim(codeHash: String, deviceId: UUID): UUID? {
        val sql = """
            UPDATE pairing_sessions
               SET status = 'claimed', claimed_by_device_id = ?, claimed_at = now()
             WHERE code_hash = ? AND status = 'pending' AND expires_at > now()
            RETURNING child_id
        """.trimIndent()

        var result: UUID? = null
        exec(
            sql,
            args = listOf(
                UUIDColumnType() to deviceId,
                TextColumnType() to codeHash,
            ),
            explicitStatementType = StatementType.SELECT,
        ) { rs ->
            if (rs.next()) {
                result = rs.getObject("child_id", UUID::class.java)
            }
        }
        return result
    }

    private fun generatePairingCode(): String {
        val random = SecureRandom()
        return buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) {
                append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)])
            }
        }
    }

    private fun mapDeviceStatus(value: String): DeviceStatus = when (value) {
        DeviceStatusValues.PENDING_APPROVAL -> DeviceStatus.PENDING_APPROVAL
        DeviceStatusValues.APPROVED -> DeviceStatus.APPROVED
        DeviceStatusValues.REVOKED -> DeviceStatus.REVOKED
        else -> error("Unknown device status in DB: $value")
    }

    private fun ResultRow.toDeviceResponse(): DeviceResponse = DeviceResponse(
        id = this[DevicesTable.id].value.toString(),
        childId = this[DevicesTable.childId].value.toString(),
        deviceName = this[DevicesTable.deviceName],
        model = this[DevicesTable.model],
        osVersion = this[DevicesTable.osVersion],
        appVersion = this[DevicesTable.appVersion],
        status = mapDeviceStatus(this[DevicesTable.status]),
        lastSeenAt = this[DevicesTable.lastSeenAt]?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        lastSyncAt = this[DevicesTable.lastSyncAt]?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        pairedAt = this[DevicesTable.pairedAt]?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        approvedAt = this[DevicesTable.approvedAt]?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

    companion object {
        // Crockford Base32: excludes ambiguous I, L, O, U. 8 chars * 5 bits = 40 bits entropy.
        private const val CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val CODE_LENGTH = 8
    }
}
