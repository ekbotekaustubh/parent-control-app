package com.familyguard.backend.services

import com.familyguard.backend.db.tables.AppsTable
import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.db.tables.UsageRecordsTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.ReportAppUsage
import com.familyguard.shared.dto.ReportDayUsage
import com.familyguard.shared.dto.ReportResponse
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID

/**
 * "Reports" (docs/roadmap.md) - pure aggregation over usage_records, no schema change.
 * Ranges are rolling windows ending at `anchor` (default today), not calendar week/month
 * boundaries - same honest simplification as InsightsService and AccessRequestService's
 * override window (no reliable device-local-timezone signal server-side to compute a real
 * calendar boundary). Aggregated in Kotlin rather than SQL GROUP BY - usage_records rows
 * per child are small in volume for this slice's scope, and it keeps this read model as
 * simple/auditable as DashboardService's.
 */
class ReportService {

    fun getReport(parentId: UUID, childId: UUID, range: String, anchor: LocalDate): ReportResponse = transaction {
        requireOwnedChild(parentId, childId)
        val (startDate, endDate) = windowFor(range, anchor)

        val rows = (UsageRecordsTable innerJoin AppsTable)
            .selectAll()
            .where { (UsageRecordsTable.childId eq childId) and (UsageRecordsTable.usageDate.between(startDate, endDate)) }
            .toList()

        val byApp = rows.groupBy { it[AppsTable.packageName] to it[AppsTable.displayName] }
            .map { (key, group) ->
                ReportAppUsage(
                    packageName = key.first,
                    displayName = key.second,
                    totalMinutes = group.sumOf { row -> row[UsageRecordsTable.durationMinutes] },
                )
            }
            .sortedByDescending { it.totalMinutes }

        val byDay = rows.groupBy { it[UsageRecordsTable.usageDate] }
            .map { (date, group) ->
                ReportDayUsage(date = date.toString(), totalMinutes = group.sumOf { row -> row[UsageRecordsTable.durationMinutes] })
            }
            .sortedBy { it.date }

        ReportResponse(
            range = range,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            totalMinutes = byApp.sumOf { it.totalMinutes },
            byApp = byApp,
            byDay = byDay,
        )
    }

    private fun windowFor(range: String, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
        "daily" -> anchor to anchor
        "weekly" -> anchor.minusDays(6) to anchor
        "monthly" -> anchor.minusDays(29) to anchor
        else -> throw ApiException.BadRequest("INVALID_RANGE", "range must be one of: daily, weekly, monthly.")
    }

    private fun requireOwnedChild(parentId: UUID, childId: UUID) {
        val owns = ChildrenTable.selectAll()
            .where { (ChildrenTable.id eq childId) and (ChildrenTable.parentId eq parentId) }
            .count() > 0
        if (!owns) throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")
    }
}
