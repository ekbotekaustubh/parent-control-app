package com.familyguard.child.data.repository

import com.familyguard.child.data.local.db.CachedCategoryLimitDao
import com.familyguard.child.data.local.db.CachedCategoryLimitEntity
import com.familyguard.child.data.local.db.CachedOverrideDao
import com.familyguard.child.data.local.db.CachedOverrideEntity
import com.familyguard.child.data.local.db.CachedRuleDao
import com.familyguard.child.data.local.db.CachedRuleEntity
import com.familyguard.child.data.local.db.CachedScheduleDao
import com.familyguard.child.data.local.db.CachedScheduleEntity
import com.familyguard.child.data.remote.ChildApiService
import com.familyguard.child.domain.EnforcementOverride
import com.familyguard.child.domain.EnforcementRule
import com.familyguard.child.domain.EnforcementSchedule
import com.familyguard.child.domain.RuleKind
import com.familyguard.child.domain.ScheduleKind
import com.familyguard.shared.enums.RuleType
import com.familyguard.shared.enums.ScheduleMode
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches `/device/config` and caches it into Room. `MonitorForegroundService` only ever
 * reads the `getCached*`/`observeCached*` accessors below — never this class's network
 * call directly — which is what keeps enforcement network-independent (requirement #2 in
 * the child-app spec).
 */
@Singleton
class RuleRepository @Inject constructor(
    private val api: ChildApiService,
    private val dao: CachedRuleDao,
    private val overrideDao: CachedOverrideDao,
    private val scheduleDao: CachedScheduleDao,
    private val categoryLimitDao: CachedCategoryLimitDao,
) {

    /** Pulls the latest rules/overrides/category-limits/schedules and replaces the local caches. Called only by RuleSyncWorker. */
    suspend fun syncFromBackend(): Result<Long> = runCatching {
        val response = api.getDeviceConfig()
        val now = System.currentTimeMillis()
        val entities = response.rules.map { rule ->
            CachedRuleEntity(
                packageName = rule.packageName,
                ruleType = rule.ruleType.name,
                dailyLimitMinutes = rule.dailyLimitMinutes,
                configVersion = response.configVersion,
                updatedAt = now,
                category = rule.category,
                scheduleId = rule.scheduleId,
            )
        }
        dao.replaceAll(entities)

        val overrideEntities = response.overrides.map { override ->
            CachedOverrideEntity(
                packageName = override.packageName,
                extraMinutes = override.extraMinutes,
                expiresAtMillis = OffsetDateTime.parse(override.expiresAt).toInstant().toEpochMilli(),
            )
        }
        overrideDao.replaceAll(overrideEntities)

        val categoryLimitEntities = response.categoryRules.map { rule ->
            CachedCategoryLimitEntity(category = rule.category, dailyLimitMinutes = rule.dailyLimitMinutes)
        }
        categoryLimitDao.replaceAll(categoryLimitEntities)

        val scheduleEntities = response.schedules.map { schedule ->
            CachedScheduleEntity(
                id = schedule.id,
                mode = schedule.mode.name,
                daysOfWeek = schedule.daysOfWeek.joinToString(",") { it.name },
                startTime = schedule.startTime,
                endTime = schedule.endTime,
            )
        }
        scheduleDao.replaceAll(scheduleEntities)

        response.configVersion
    }

    fun observeCachedRules(): Flow<List<EnforcementRule>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getCachedRules(): List<EnforcementRule> = dao.getAll().map { it.toDomain() }

    suspend fun getCachedOverrides(): List<EnforcementOverride> =
        overrideDao.getActive(System.currentTimeMillis()).map {
            EnforcementOverride(packageName = it.packageName, extraMinutes = it.extraMinutes)
        }

    suspend fun getCachedCategoryLimits(): Map<String, Int> =
        categoryLimitDao.getAll().associate { it.category to it.dailyLimitMinutes }

    suspend fun getCachedSchedules(): List<EnforcementSchedule> = scheduleDao.getAll().map { it.toDomain() }

    private fun CachedRuleEntity.toDomain(): EnforcementRule = EnforcementRule(
        packageName = packageName,
        ruleType = when (RuleType.valueOf(ruleType)) {
            RuleType.BLOCK -> RuleKind.BLOCK
            RuleType.ALLOW -> RuleKind.ALLOW
        },
        dailyLimitMinutes = dailyLimitMinutes,
        category = category,
        scheduleId = scheduleId,
    )

    private fun CachedScheduleEntity.toDomain(): EnforcementSchedule = EnforcementSchedule(
        id = id,
        mode = when (ScheduleMode.valueOf(mode)) {
            ScheduleMode.BLOCK -> ScheduleKind.BLOCK
            ScheduleMode.ALLOW_ONLY -> ScheduleKind.ALLOW_ONLY
        },
        daysOfWeek = daysOfWeek.split(",").filter { it.isNotBlank() }.map { toJavaDayOfWeek(it) }.toSet(),
        startTime = LocalTime.parse(startTime),
        endTime = LocalTime.parse(endTime),
    )

    /** Shared's `Weekday` enum names (MON..SUN) map 1:1 to `java.time.DayOfWeek`'s three-letter prefix. */
    private fun toJavaDayOfWeek(code: String): DayOfWeek = when (code) {
        "MON" -> DayOfWeek.MONDAY
        "TUE" -> DayOfWeek.TUESDAY
        "WED" -> DayOfWeek.WEDNESDAY
        "THU" -> DayOfWeek.THURSDAY
        "FRI" -> DayOfWeek.FRIDAY
        "SAT" -> DayOfWeek.SATURDAY
        "SUN" -> DayOfWeek.SUNDAY
        else -> error("Unknown day-of-week code in cache: $code")
    }
}
