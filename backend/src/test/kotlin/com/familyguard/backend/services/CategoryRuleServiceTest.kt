package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.UpsertCategoryRuleRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class CategoryRuleServiceTest {

    private val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
    private val authService = AuthService(jwtConfig)
    private val childService = ChildService()
    private val categoryRuleService = CategoryRuleService()

    private fun newParentAndChild(): Pair<UUID, UUID> {
        val signup = authService.signup(SignupRequest("category-rule-test-${UUID.randomUUID()}@example.com", "password123", "Parent"))
        val parentId = UUID.fromString(signup.parentId)
        val child = childService.createChild(parentId, CreateChildRequest("Kid", 2015))
        return parentId to UUID.fromString(child.id)
    }

    @Test
    fun `upserting a new category rule creates it`() {
        val (parentId, childId) = newParentAndChild()

        val rule = categoryRuleService.upsertCategoryRule(parentId, childId, "social", UpsertCategoryRuleRequest(dailyLimitMinutes = 60))

        assertEquals("social", rule.category)
        assertEquals(60, rule.dailyLimitMinutes)
    }

    @Test
    fun `upserting the same category again updates in place, one row`() {
        val (parentId, childId) = newParentAndChild()
        categoryRuleService.upsertCategoryRule(parentId, childId, "social", UpsertCategoryRuleRequest(dailyLimitMinutes = 60))
        categoryRuleService.upsertCategoryRule(parentId, childId, "social", UpsertCategoryRuleRequest(dailyLimitMinutes = 30))

        val all = categoryRuleService.listCategoryRules(childId)
        assertEquals(1, all.count { it.category == "social" })
        assertEquals(30, all.single { it.category == "social" }.dailyLimitMinutes)
    }

    @Test
    fun `deleting a category rule removes it from the list`() {
        val (parentId, childId) = newParentAndChild()
        categoryRuleService.upsertCategoryRule(parentId, childId, "games", UpsertCategoryRuleRequest(dailyLimitMinutes = 45))

        categoryRuleService.deleteCategoryRule(parentId, childId, "games")

        assertTrue(categoryRuleService.listCategoryRules(childId).none { it.category == "games" })
    }

    @Test
    fun `a non-owning parent gets NOT_FOUND, never a leak of the child's existence`() {
        val (_, childId) = newParentAndChild()
        val (otherParentId, _) = newParentAndChild()

        val ex = assertThrows(ApiException.NotFound::class.java) {
            categoryRuleService.upsertCategoryRule(otherParentId, childId, "social", UpsertCategoryRuleRequest(dailyLimitMinutes = 60))
        }
        assertEquals("CHILD_NOT_FOUND", ex.code)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpDatabase() {
            DatabaseFactory.init(
                jdbcUrl = "jdbc:postgresql://localhost:5432/familyguard",
                user = "familyguard",
                password = "familyguard",
            )
        }
    }
}
