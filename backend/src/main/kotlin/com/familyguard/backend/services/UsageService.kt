package com.familyguard.backend.services

import com.familyguard.backend.db.tables.AppsTable
import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.db.tables.DevicesTable
import com.familyguard.backend.db.tables.UsageRecordsTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.HeartbeatResponse
import com.familyguard.shared.dto.UsageEvent
import com.familyguard.shared.dto.UsageSyncResponse
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * `POST /device/usage-sync`'s upsert must be provably idempotent: replaying the same batch
 * twice must not change the stored value beyond what applying it once did. Written as a
 * native `INSERT ... ON CONFLICT (device_id, app_id, usage_date) DO UPDATE SET
 * duration_minutes = GREATEST(...)` so that's true by construction - the max of a value
 * with itself is itself, so a duplicate delivery is a safe no-op past the first application.
 */
class UsageService {

    fun syncUsage(deviceId: UUID, childId: UUID, events: List<UsageEvent>): UsageSyncResponse = transaction {
        var accepted = 0
        for (event in events) {
            val usageDate = try {
                LocalDate.parse(event.usageDate)
            } catch (e: DateTimeParseException) {
                throw ApiException.BadRequest("INVALID_USAGE_DATE", "usageDate must be yyyy-MM-dd: ${event.packageName}")
            }
            val appId = findOrCreateApp(event.packageName)
            upsertUsageRecord(deviceId, childId, appId, usageDate, event.durationMinutes)
            accepted++
        }

        val now = OffsetDateTime.now()
        DevicesTable.update({ DevicesTable.id eq deviceId }) {
            it[lastSyncAt] = now
            it[lastSeenAt] = now
        }

        UsageSyncResponse(accepted, now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    }

    fun heartbeat(deviceId: UUID, childId: UUID): HeartbeatResponse = transaction {
        DevicesTable.update({ DevicesTable.id eq deviceId }) {
            it[lastSeenAt] = OffsetDateTime.now()
        }
        val configVersion = ChildrenTable.selectAll()
            .where { ChildrenTable.id eq childId }
            .single()[ChildrenTable.configVersion]
        HeartbeatResponse(ok = true, configVersion = configVersion)
    }

    private fun findOrCreateApp(packageName: String): UUID {
        val existing = AppsTable.selectAll().where { AppsTable.packageName eq packageName }.singleOrNull()
        if (existing != null) return existing[AppsTable.id].value

        return try {
            AppsTable.insertAndGetId { it[AppsTable.packageName] = packageName }.value
        } catch (e: Exception) {
            AppsTable.selectAll().where { AppsTable.packageName eq packageName }.single()[AppsTable.id].value
        }
    }

    /**
     * The GREATEST-upsert itself, as literal SQL (per docs/architecture.md and
     * database-schema.md) rather than Exposed's `upsert()` DSL, so the exact
     * `GREATEST(excluded.duration_minutes, usage_records.duration_minutes)` semantics are
     * unambiguous and match the documented statement precisely.
     */
    private fun Transaction.upsertUsageRecord(
        deviceId: UUID,
        childId: UUID,
        appId: UUID,
        usageDate: LocalDate,
        durationMinutes: Int,
    ) {
        val sql = """
            INSERT INTO usage_records (id, device_id, child_id, app_id, usage_date, duration_minutes, last_updated_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (device_id, app_id, usage_date)
            DO UPDATE SET
                duration_minutes = GREATEST(excluded.duration_minutes, usage_records.duration_minutes),
                last_updated_at = now()
        """.trimIndent()

        exec(
            sql,
            args = listOf(
                UUIDColumnType() to UUID.randomUUID(),
                UUIDColumnType() to deviceId,
                UUIDColumnType() to childId,
                UUIDColumnType() to appId,
                UsageRecordsTable.usageDate.columnType to usageDate,
                IntegerColumnType() to durationMinutes,
            ),
        )
    }
}
