package com.familyguard.backend.services

import com.familyguard.backend.db.tables.CategoryRulesTable
import com.familyguard.backend.db.tables.ChildrenTable
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.CategoryRuleResponse
import com.familyguard.shared.dto.UpsertCategoryRuleRequest
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * "Category-level rules and limits" (docs/roadmap.md). Applies as a fallback for apps that
 * already have a standing ALLOW rule with no explicit daily limit - see the child app's
 * `EnforcementDecider` KDoc for the honest scope limit on why an app with no rule at all
 * isn't affected by its category (the device only learns a package's category via its
 * synced app_rules row).
 */
class CategoryRuleService {

    /** Upsert on (child_id, category). Does NOT bump children.config_version by itself - GET /device/config always includes the current categoryRules regardless. */
    fun upsertCategoryRule(parentId: UUID, childId: UUID, category: String, request: UpsertCategoryRuleRequest): CategoryRuleResponse =
        transaction {
            requireOwnedChild(parentId, childId)

            val existing = CategoryRulesTable.selectAll()
                .where { (CategoryRulesTable.childId eq childId) and (CategoryRulesTable.category eq category) }
                .singleOrNull()

            if (existing == null) {
                CategoryRulesTable.insert {
                    it[CategoryRulesTable.childId] = childId
                    it[CategoryRulesTable.category] = category
                    it[dailyLimitMinutes] = request.dailyLimitMinutes
                }
            } else {
                CategoryRulesTable.update({ CategoryRulesTable.id eq existing[CategoryRulesTable.id] }) {
                    it[dailyLimitMinutes] = request.dailyLimitMinutes
                }
            }

            CategoryRulesTable.selectAll()
                .where { (CategoryRulesTable.childId eq childId) and (CategoryRulesTable.category eq category) }
                .single()
                .toResponse()
        }

    fun deleteCategoryRule(parentId: UUID, childId: UUID, category: String) {
        transaction {
            requireOwnedChild(parentId, childId)
            CategoryRulesTable.deleteWhere { (CategoryRulesTable.childId eq childId) and (CategoryRulesTable.category eq category) }
        }
    }

    /** Callable by the owning parent OR a device whose JWT childId claim matches - same split as RuleService.listRules. */
    fun listCategoryRules(childId: UUID): List<CategoryRuleResponse> = transaction {
        CategoryRulesTable.selectAll()
            .where { CategoryRulesTable.childId eq childId }
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

    private fun ResultRow.toResponse(): CategoryRuleResponse = CategoryRuleResponse(
        id = this[CategoryRulesTable.id].value.toString(),
        childId = this[CategoryRulesTable.childId].value.toString(),
        category = this[CategoryRulesTable.category],
        dailyLimitMinutes = this[CategoryRulesTable.dailyLimitMinutes],
    )
}
