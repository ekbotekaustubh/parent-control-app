package com.familyguard.backend.services

import com.familyguard.backend.db.tables.AppsTable
import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.db.tables.DevicesTable
import com.familyguard.backend.db.tables.UsageRecordsTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.DashboardResponse
import com.familyguard.shared.dto.DeviceStatusInfo
import com.familyguard.shared.dto.UsageTodayItem
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Purpose-built read model for the parent's one dashboard screen (api-spec.md
 * "GET /children/{childId}/dashboard") - joins device status, rules, and today's usage in a
 * single call rather than making the client do N+1 requests.
 */
class DashboardService(private val ruleService: RuleService = RuleService()) {

    // Not pinned by any doc: a device counts as "online" if it's been seen within 2x the
    // default heartbeat/sync interval (DEVICE_CONFIG's syncIntervalSeconds = 900s), which
    // tolerates one missed beat before flipping to offline.
    private val onlineThresholdMinutes = 30L

    fun getDashboard(parentId: UUID, childId: UUID): DashboardResponse = transaction {
        ChildrenTable.selectAll()
            .where { (ChildrenTable.id eq childId) and (ChildrenTable.parentId eq parentId) }
            .singleOrNull()
            ?: throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")

        // A child may (per roadmap.md) eventually have multiple devices; this slice's
        // dashboard shows the most recently created one, consistent with the single
        // `device` object in api-spec.md's documented response shape.
        val deviceRow = DevicesTable.selectAll()
            .where { DevicesTable.childId eq childId }
            .orderBy(DevicesTable.createdAt to SortOrder.DESC)
            .firstOrNull()

        val deviceInfo = if (deviceRow == null) {
            DeviceStatusInfo(status = "unpaired", online = false, lastSeenAt = null, lastSyncAt = null)
        } else {
            val lastSeenAt = deviceRow[DevicesTable.lastSeenAt]
            val online = lastSeenAt != null &&
                lastSeenAt.isAfter(OffsetDateTime.now().minusMinutes(onlineThresholdMinutes))
            DeviceStatusInfo(
                status = deviceRow[DevicesTable.status],
                online = online,
                lastSeenAt = lastSeenAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                lastSyncAt = deviceRow[DevicesTable.lastSyncAt]?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
        }

        val rules = ruleService.listRules(childId)
        val ruleByPackage = rules.associateBy { it.packageName }

        // NOTE (assumption): database-schema.md says usage_date is "the child device's local
        // day", but parents.timezone is explicitly "unused this slice" (per that same doc),
        // so there's no per-child timezone to convert with yet. This slice uses the
        // backend's current UTC date as "today" for the dashboard query - consistent with
        // the rest of this vertical slice not doing timezone-aware day-boundary logic.
        val today = LocalDate.now()
        val usageToday = (UsageRecordsTable innerJoin AppsTable)
            .selectAll()
            .where { (UsageRecordsTable.childId eq childId) and (UsageRecordsTable.usageDate eq today) }
            .map { row ->
                val packageName = row[AppsTable.packageName]
                val duration = row[UsageRecordsTable.durationMinutes]
                val limit = ruleByPackage[packageName]?.dailyLimitMinutes
                UsageTodayItem(
                    packageName = packageName,
                    displayName = row[AppsTable.displayName],
                    durationMinutes = duration,
                    dailyLimitMinutes = limit,
                    isOverLimit = limit != null && duration > limit,
                )
            }

        DashboardResponse(deviceInfo, rules, usageToday)
    }
}
