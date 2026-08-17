package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.UpsertRuleRequest
import com.familyguard.shared.enums.RuleType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class RuleServiceTest {

    private val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
    private val authService = AuthService(jwtConfig)
    private val childService = ChildService()
    private val ruleService = RuleService()

    private fun newParentAndChild(): Pair<UUID, UUID> {
        val signup = authService.signup(SignupRequest("rule-test-${UUID.randomUUID()}@example.com", "password123", "Parent"))
        val parentId = UUID.fromString(signup.parentId)
        val child = childService.createChild(parentId, CreateChildRequest("Kid", 2015))
        return parentId to UUID.fromString(child.id)
    }

    @Test
    fun `upserting a new rule creates it and bumps config_version`() {
        val (parentId, childId) = newParentAndChild()
        val before = childService.getOwnedChild(parentId, childId).configVersion

        val rule = ruleService.upsertRule(
            parentId,
            childId,
            "com.google.android.youtube",
            UpsertRuleRequest(RuleType.ALLOW, dailyLimitMinutes = 45),
        )

        assertEquals("com.google.android.youtube", rule.packageName)
        assertEquals(RuleType.ALLOW, rule.ruleType)
        assertEquals(45, rule.dailyLimitMinutes)

        val after = childService.getOwnedChild(parentId, childId).configVersion
        assertTrue(after > before)
    }

    @Test
    fun `upserting the same package again updates in place (one row) and bumps version again`() {
        val (parentId, childId) = newParentAndChild()
        ruleService.upsertRule(parentId, childId, "com.example.app", UpsertRuleRequest(RuleType.BLOCK))
        val versionAfterFirst = childService.getOwnedChild(parentId, childId).configVersion

        val updated = ruleService.upsertRule(
            parentId,
            childId,
            "com.example.app",
            UpsertRuleRequest(RuleType.ALLOW, dailyLimitMinutes = 30),
        )
        val versionAfterSecond = childService.getOwnedChild(parentId, childId).configVersion

        assertEquals(RuleType.ALLOW, updated.ruleType)
        assertEquals(30, updated.dailyLimitMinutes)
        assertTrue(versionAfterSecond > versionAfterFirst)

        val allRules = ruleService.listRules(childId)
        assertEquals(1, allRules.count { it.packageName == "com.example.app" })
    }

    @Test
    fun `deleting a rule bumps config_version and removes it from listRules`() {
        val (parentId, childId) = newParentAndChild()
        ruleService.upsertRule(parentId, childId, "com.example.todelete", UpsertRuleRequest(RuleType.BLOCK))
        val versionBeforeDelete = childService.getOwnedChild(parentId, childId).configVersion

        ruleService.deleteRule(parentId, childId, "com.example.todelete")

        val versionAfterDelete = childService.getOwnedChild(parentId, childId).configVersion
        assertTrue(versionAfterDelete > versionBeforeDelete)
        assertTrue(ruleService.listRules(childId).none { it.packageName == "com.example.todelete" })
    }

    @Test
    fun `deleting a rule for a package that was never ruled is a no-op, not an error`() {
        val (parentId, childId) = newParentAndChild()
        ruleService.deleteRule(parentId, childId, "com.example.never-existed")
    }

    @Test
    fun `a non-owning parent gets NOT_FOUND, never a leak of the child's existence`() {
        val (_, childId) = newParentAndChild()
        val (otherParentId, _) = newParentAndChild()

        val ex = assertThrows(ApiException.NotFound::class.java) {
            ruleService.upsertRule(otherParentId, childId, "com.example.app", UpsertRuleRequest(RuleType.BLOCK))
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
