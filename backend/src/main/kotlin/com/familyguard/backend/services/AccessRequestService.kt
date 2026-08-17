package com.familyguard.backend.services

import com.familyguard.backend.db.tables.AccessRequestStatusValues
import com.familyguard.backend.db.tables.AccessRequestsTable
import com.familyguard.backend.db.tables.AppsTable
import com.familyguard.backend.db.tables.AuditActorType
import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.AccessRequestResponse
import com.familyguard.shared.dto.CreateAccessRequestRequest
import com.familyguard.shared.dto.ResolveAccessRequestRequest
import com.familyguard.shared.enums.AccessRequestStatus
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * "Child requests more time" (docs/roadmap.md). A device (already blocked/limited by a
 * standing app_rules row) files a request; the owning parent approves or denies it. This
 * table is intentionally separate from app_rules - a request is a one-off ask, not a
 * change to the standing rule.
 */
class AccessRequestService {

    /** childId is trusted here - it comes from the device's own verified JWT (see routes). */
    fun createRequest(childId: UUID, request: CreateAccessRequestRequest): AccessRequestResponse = transaction {
        val appId = AppsTable.selectAll().where { AppsTable.packageName eq request.packageName }
            .singleOrNull()?.get(AppsTable.id)?.value
            ?: throw ApiException.NotFound("APP_NOT_FOUND", "No such app.")

        val id = AccessRequestsTable.insertAndGetId {
            it[AccessRequestsTable.childId] = childId
            it[AccessRequestsTable.appId] = appId
            it[requestedMinutes] = request.requestedMinutes
            it[status] = AccessRequestStatusValues.PENDING
        }.value

        AuditService.log(AuditActorType.DEVICE, null, "access_request.created", "access_request", id)

        fetchById(id)
    }

    /** Pending requests across every child owned by this parent, per api-spec.md. */
    fun listPendingForParent(parentId: UUID): List<AccessRequestResponse> = transaction {
        (AccessRequestsTable innerJoin ChildrenTable innerJoin AppsTable)
            .selectAll()
            .where { (ChildrenTable.parentId eq parentId) and (AccessRequestsTable.status eq AccessRequestStatusValues.PENDING) }
            .map { it.toResponse() }
    }

    /**
     * Parent-only, ownership-checked via the request's child. Atomic conditional UPDATE
     * (mirrors PairingService.attemptAtomicClaim's approach) so two concurrent resolves of
     * the same request can't both "win".
     */
    fun resolve(parentId: UUID, requestId: UUID, request: ResolveAccessRequestRequest): AccessRequestResponse = transaction {
        val owned = (AccessRequestsTable innerJoin ChildrenTable)
            .selectAll()
            .where { (AccessRequestsTable.id eq requestId) and (ChildrenTable.parentId eq parentId) }
            .singleOrNull()
            ?: throw ApiException.NotFound("ACCESS_REQUEST_NOT_FOUND", "No such access request.")

        val newStatus = if (request.approve) AccessRequestStatusValues.APPROVED else AccessRequestStatusValues.DENIED
        val resolvedMinutes = if (request.approve) {
            request.resolvedMinutes ?: owned[AccessRequestsTable.requestedMinutes]
        } else {
            null
        }

        val updated = AccessRequestsTable.update({
            (AccessRequestsTable.id eq requestId) and (AccessRequestsTable.status eq AccessRequestStatusValues.PENDING)
        }) {
            it[status] = newStatus
            it[AccessRequestsTable.resolvedMinutes] = resolvedMinutes
            it[resolvedAt] = OffsetDateTime.now()
        }
        if (updated == 0) {
            throw ApiException.Conflict("ACCESS_REQUEST_ALREADY_RESOLVED", "This request has already been resolved.")
        }

        AuditService.log(AuditActorType.PARENT, parentId, "access_request.resolved", "access_request", requestId)

        fetchById(requestId)
    }

    private fun fetchById(id: UUID): AccessRequestResponse =
        (AccessRequestsTable innerJoin AppsTable)
            .selectAll()
            .where { AccessRequestsTable.id eq id }
            .single()
            .toResponse()

    private fun ResultRow.toResponse(): AccessRequestResponse = AccessRequestResponse(
        id = this[AccessRequestsTable.id].value.toString(),
        childId = this[AccessRequestsTable.childId].value.toString(),
        packageName = this[AppsTable.packageName],
        displayName = this[AppsTable.displayName],
        requestedMinutes = this[AccessRequestsTable.requestedMinutes],
        status = mapStatus(this[AccessRequestsTable.status]),
        resolvedMinutes = this[AccessRequestsTable.resolvedMinutes],
        createdAt = this[AccessRequestsTable.createdAt].format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        resolvedAt = this[AccessRequestsTable.resolvedAt]?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )

    private fun mapStatus(value: String): AccessRequestStatus = when (value) {
        AccessRequestStatusValues.PENDING -> AccessRequestStatus.PENDING
        AccessRequestStatusValues.APPROVED -> AccessRequestStatus.APPROVED
        AccessRequestStatusValues.DENIED -> AccessRequestStatus.DENIED
        else -> error("Unknown access_request status in DB: $value")
    }
}
