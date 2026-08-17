package com.familyguard.child.data.repository

import com.familyguard.child.data.local.db.CachedRuleDao
import com.familyguard.child.data.local.db.CachedRuleEntity
import com.familyguard.child.data.remote.ChildApiService
import com.familyguard.child.domain.EnforcementRule
import com.familyguard.child.domain.RuleKind
import com.familyguard.shared.enums.RuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches `/device/config` and caches it into Room. `MonitorForegroundService` only ever
 * reads [observeCachedRules]/[getCachedRules] — never this class's network call directly —
 * which is what keeps enforcement network-independent (requirement #2 in the child-app spec).
 */
@Singleton
class RuleRepository @Inject constructor(
    private val api: ChildApiService,
    private val dao: CachedRuleDao,
) {

    /** Pulls the latest rule set from the backend and replaces the local cache. Called only by RuleSyncWorker. */
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
        response.configVersion
    }

    fun observeCachedRules(): Flow<List<EnforcementRule>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getCachedRules(): List<EnforcementRule> = dao.getAll().map { it.toDomain() }

    private fun CachedRuleEntity.toDomain(): EnforcementRule = EnforcementRule(
        packageName = packageName,
        ruleType = when (RuleType.valueOf(ruleType)) {
            RuleType.BLOCK -> RuleKind.BLOCK
            RuleType.ALLOW -> RuleKind.ALLOW
        },
        dailyLimitMinutes = dailyLimitMinutes,
    )
}
