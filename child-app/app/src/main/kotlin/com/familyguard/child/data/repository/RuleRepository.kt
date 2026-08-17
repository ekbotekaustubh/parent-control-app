package com.familyguard.child.data.repository

import com.familyguard.child.data.local.db.CachedOverrideDao
import com.familyguard.child.data.local.db.CachedOverrideEntity
import com.familyguard.child.data.local.db.CachedRuleDao
import com.familyguard.child.data.local.db.CachedRuleEntity
import com.familyguard.child.data.remote.ChildApiService
import com.familyguard.child.domain.EnforcementOverride
import com.familyguard.child.domain.EnforcementRule
import com.familyguard.child.domain.RuleKind
import com.familyguard.shared.enums.RuleType
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches `/device/config` and caches it into Room. `MonitorForegroundService` only ever
 * reads [observeCachedRules]/[getCachedRules]/[getCachedOverrides] — never this class's
 * network call directly — which is what keeps enforcement network-independent
 * (requirement #2 in the child-app spec).
 */
@Singleton
class RuleRepository @Inject constructor(
    private val api: ChildApiService,
    private val dao: CachedRuleDao,
    private val overrideDao: CachedOverrideDao,
) {

    /** Pulls the latest rule set (and active access-request overrides) and replaces the local caches. Called only by RuleSyncWorker. */
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

        response.configVersion
    }

    fun observeCachedRules(): Flow<List<EnforcementRule>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getCachedRules(): List<EnforcementRule> = dao.getAll().map { it.toDomain() }

    suspend fun getCachedOverrides(): List<EnforcementOverride> =
        overrideDao.getActive(System.currentTimeMillis()).map {
            EnforcementOverride(packageName = it.packageName, extraMinutes = it.extraMinutes)
        }

    private fun CachedRuleEntity.toDomain(): EnforcementRule = EnforcementRule(
        packageName = packageName,
        ruleType = when (RuleType.valueOf(ruleType)) {
            RuleType.BLOCK -> RuleKind.BLOCK
            RuleType.ALLOW -> RuleKind.ALLOW
        },
        dailyLimitMinutes = dailyLimitMinutes,
    )
}
