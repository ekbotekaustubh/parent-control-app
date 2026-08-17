package com.familyguard.backend.services

import com.familyguard.backend.db.tables.AppRulesTable
import com.familyguard.backend.db.tables.AppsTable
import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.db.tables.RuleTypeValues
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.AppRuleResponse
import com.familyguard.shared.dto.UpsertRuleRequest
import com.familyguard.shared.enums.RuleType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class RuleService {

    /** Upsert on (child_id, app_id). Always bumps children.config_version, per api-spec.md. */
    fun upsertRule(parentId: UUID, childId: UUID, packageName: String, request: UpsertRuleRequest): AppRuleResponse =
        transaction {
            requireOwnedChild(parentId, childId)
            val appId = findOrCreateApp(packageName)

            val existing = AppRulesTable.selectAll()
                .where { (AppRulesTable.childId eq childId) and (AppRulesTable.appId eq appId) }
                .singleOrNull()

            val ruleTypeValue = if (request.ruleType == RuleType.ALLOW) RuleTypeValues.ALLOW else RuleTypeValues.BLOCK

            if (existing == null) {
                AppRulesTable.insert {
                    it[AppRulesTable.childId] = childId
                    it[AppRulesTable.appId] = appId
                    it[ruleType] = ruleTypeValue
                    it[dailyLimitMinutes] = request.dailyLimitMinutes
                    it[isActive] = true
                }
            } else {
                AppRulesTable.update({ AppRulesTable.id eq existing[AppRulesTable.id] }) {
                    it[ruleType] = ruleTypeValue
                    it[dailyLimitMinutes] = request.dailyLimitMinutes
                    it[isActive] = true
                }
            }

            bumpConfigVersion(childId)

            val row = (AppRulesTable innerJoin AppsTable)
                .selectAll()
                .where { (AppRulesTable.childId eq childId) and (AppRulesTable.appId eq appId) }
                .single()
            row.toResponse()
        }

    fun deleteRule(parentId: UUID, childId: UUID, packageName: String) {
        transaction {
            requireOwnedChild(parentId, childId)
            val appId = AppsTable.selectAll().where { AppsTable.packageName eq packageName }.singleOrNull()
                ?.get(AppsTable.id)?.value
                ?: return@transaction // No such app in the catalog at all -> no rule could exist; no-op.

            val deleted = AppRulesTable.deleteWhere { (AppRulesTable.childId eq childId) and (AppRulesTable.appId eq appId) }
            if (deleted > 0) {
                bumpConfigVersion(childId)
            }
        }
    }

    /**
     * Callable by the owning parent OR a device whose JWT childId claim matches (api-spec.md).
     * Ownership/claim-matching is verified by the caller (route) before invoking this - this
     * function only requires a childId, already established as authorized to see.
     */
    fun listRules(childId: UUID): List<AppRuleResponse> = transaction {
        (AppRulesTable innerJoin AppsTable)
            .selectAll()
            .where { (AppRulesTable.childId eq childId) and (AppRulesTable.isActive eq true) }
            .map { it.toResponse() }
    }

    fun isOwnedChild(parentId: UUID, childId: UUID): Boolean = transaction {
        ChildrenTable.selectAll()
            .where { (ChildrenTable.id eq childId) and (ChildrenTable.parentId eq parentId) }
            .count() > 0
    }

    private fun requireOwnedChild(parentId: UUID, childId: UUID) {
        if (!isOwnedChild(parentId, childId)) {
            throw ApiException.NotFound("CHILD_NOT_FOUND", "No child with that id.")
        }
    }

    private fun bumpConfigVersion(childId: UUID) {
        ChildrenTable.update({ ChildrenTable.id eq childId }) {
            it.update(ChildrenTable.configVersion, ChildrenTable.configVersion + 1L)
        }
    }

    /** Find-or-create in the global apps catalog by package name (used by rule upserts). */
    private fun findOrCreateApp(packageName: String): UUID {
        val existing = AppsTable.selectAll().where { AppsTable.packageName eq packageName }.singleOrNull()
        if (existing != null) return existing[AppsTable.id].value

        return try {
            AppsTable.insertAndGetId { it[AppsTable.packageName] = packageName }.value
        } catch (e: Exception) {
            // Race: another request created the same package_name concurrently (unique constraint).
            AppsTable.selectAll().where { AppsTable.packageName eq packageName }.single()[AppsTable.id].value
        }
    }

    private fun ResultRow.toResponse(): AppRuleResponse = AppRuleResponse(
        id = this[AppRulesTable.id].value.toString(),
        childId = this[AppRulesTable.childId].value.toString(),
        packageName = this[AppsTable.packageName],
        displayName = this[AppsTable.displayName],
        ruleType = if (this[AppRulesTable.ruleType] == RuleTypeValues.ALLOW) RuleType.ALLOW else RuleType.BLOCK,
        dailyLimitMinutes = this[AppRulesTable.dailyLimitMinutes],
        isActive = this[AppRulesTable.isActive],
    )
}
