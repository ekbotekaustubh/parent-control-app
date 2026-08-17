package com.familyguard.backend.services

import com.familyguard.backend.db.tables.AppsTable
import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.db.tables.UsageRecordsTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.InsightsResponse
import com.familyguard.shared.dto.TopAppUsage
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * "Smart insights" (docs/roadmap.md) - purely a read-side rollup over usage_records, no new
 * tables. "This week"/"last week" are rolling 7-day windows ending today, not calendar
 * weeks - see ReportService's KDoc for why (no device-local-timezone signal server-side).
 */
class InsightsService {

    fun getInsights(parentId: UUID, childId: UUID): InsightsResponse = transaction {
        requireOwnedChild(parentId, childId)

        val today = LocalDate.now()
        val currentWeekStart = today.minusDays(6)
        val previousWeekEnd = currentWeekStart.minusDays(1)
        val previousWeekStart = previousWeekEnd.minusDays(6)

        val currentWeekRows = usageRowsBetween(childId, currentWeekStart, today)
        val previousWeekRows = usageRowsBetween(childId, previousWeekStart, previousWeekEnd)

        val currentWeekMinutes = currentWeekRows.sumOf { it[UsageRecordsTable.durationMinutes] }
        val previousWeekMinutes = previousWeekRows.sumOf { it[UsageRecordsTable.durationMinutes] }

        val changePercent = if (previousWeekMinutes == 0) {
            null
        } else {
            ((currentWeekMinutes - previousWeekMinutes).toDouble() / previousWeekMinutes) * 100.0
        }

        val topApps = currentWeekRows
            .groupBy { it[AppsTable.packageName] to it[AppsTable.displayName] }
            .map { (key, group) ->
                TopAppUsage(packageName = key.first, displayName = key.second, minutes = group.sumOf { row -> row[UsageRecordsTable.durationMinutes] })
            }
            .sortedByDescending { it.minutes }
            .take(3)

        InsightsResponse(
            currentWeekMinutes = currentWeekMinutes,
            previousWeekMinutes = previousWeekMinutes,
            weekOverWeekChangePercent = changePercent,
            topApps = topApps,
            generatedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    private fun usageRowsBetween(childId: UUID, start: LocalDate, end: LocalDate): List<ResultRow> =
        (UsageRecordsTable innerJoin AppsTable)
            .selectAll()
            .where { (UsageRecordsTable.childId eq childId) and (UsageRecordsTable.usageDate.between(start, end)) }
            .toList()

    private fun requireOwnedChild(parentId: UUID, childId: UUID) {
        val owns = ChildrenTable.selectAll()
            .where { (ChildrenTable.id eq childId) and (ChildrenTable.parentId eq parentId) }
            .count() > 0
        if (!owns) throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")
    }
}
