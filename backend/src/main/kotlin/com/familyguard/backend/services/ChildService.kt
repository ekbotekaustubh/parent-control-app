package com.familyguard.backend.services

import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.ChildResponse
import com.familyguard.shared.dto.CreateChildRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.format.DateTimeFormatter
import java.util.UUID

class ChildService {

    fun createChild(parentId: UUID, request: CreateChildRequest): ChildResponse = transaction {
        val childId = ChildrenTable.insertAndGetId {
            it[ChildrenTable.parentId] = parentId
            it[name] = request.name
            it[birthYear] = request.birthYear
        }.value

        val row = ChildrenTable.selectAll().where { ChildrenTable.id eq childId }.single()
        row.toResponse()
    }

    fun listChildren(parentId: UUID): List<ChildResponse> = transaction {
        ChildrenTable.selectAll()
            .where { ChildrenTable.parentId eq parentId }
            .map { it.toResponse() }
    }

    /**
     * Returns null (never throws) so callers can decide between plain 404 responses; the
     * anti-IDOR rule (never 403) means "doesn't exist" and "exists but isn't yours" render
     * identically to the caller.
     */
    fun getOwnedChildOrNull(parentId: UUID, childId: UUID): ChildResponse? = transaction {
        ChildrenTable.selectAll()
            .where { (ChildrenTable.id eq childId) and (ChildrenTable.parentId eq parentId) }
            .singleOrNull()
            ?.toResponse()
    }

    fun getOwnedChild(parentId: UUID, childId: UUID): ChildResponse =
        getOwnedChildOrNull(parentId, childId)
            ?: throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")

    /** Used by GET /device/config - childId is trusted here because it came from the device's own JWT. */
    fun getConfigVersion(childId: UUID): Long = transaction {
        ChildrenTable.selectAll()
            .where { ChildrenTable.id eq childId }
            .single()[ChildrenTable.configVersion]
    }

    private fun ResultRow.toResponse(): ChildResponse = ChildResponse(
        id = this[ChildrenTable.id].value.toString(),
        parentId = this[ChildrenTable.parentId].value.toString(),
        name = this[ChildrenTable.name],
        birthYear = this[ChildrenTable.birthYear],
        configVersion = this[ChildrenTable.configVersion],
        status = this[ChildrenTable.status],
        createdAt = this[ChildrenTable.createdAt].format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    )
}
