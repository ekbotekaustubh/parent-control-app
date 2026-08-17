package com.familyguard.backend.services

import com.familyguard.backend.auth.JwtConfig
import com.familyguard.backend.db.DatabaseFactory
import com.familyguard.backend.plugins.ApiException
import com.familyguard.shared.dto.CreateAccessRequestRequest
import com.familyguard.shared.dto.CreateChildRequest
import com.familyguard.shared.dto.ResolveAccessRequestRequest
import com.familyguard.shared.dto.SignupRequest
import com.familyguard.shared.dto.UpsertRuleRequest
import com.familyguard.shared.enums.AccessRequestStatus
import com.familyguard.shared.enums.RuleType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class AccessRequestServiceTest {

    private val jwtConfig = JwtConfig(secret = "test-secret-not-for-prod", issuer = "familyguard-test", accessTokenTtlSeconds = 1200)
    private val authService = AuthService(jwtConfig)
    private val childService = ChildService()
    private val ruleService = RuleService()
    private val accessRequestService = AccessRequestService()

    /** Mirrors the real flow: a rule must exist (and so must the app catalog row) before a device can ask for more time on it. */
    private fun newParentChildAndRuledApp(packageName: String = "com.example.app-${UUID.randomUUID()}"): Triple<UUID, UUID, String> {
        val signup = authService.signup(SignupRequest("access-request-test-${UUID.randomUUID()}@example.com", "password123", "Parent"))
        val parentId = UUID.fromString(signup.parentId)
        val child = childService.createChild(parentId, CreateChildRequest("Kid", 2015))
        val childId = UUID.fromString(child.id)
        ruleService.upsertRule(parentId, childId, packageName, UpsertRuleRequest(RuleType.ALLOW, dailyLimitMinutes = 30))
        return Triple(parentId, childId, packageName)
    }

    @Test
    fun `a device can create a request and it shows up as pending for the owning parent`() {
        val (parentId, childId, packageName) = newParentChildAndRuledApp()

        val created = accessRequestService.createRequest(childId, CreateAccessRequestRequest(packageName, requestedMinutes = 20))
        assertEquals(AccessRequestStatus.PENDING, created.status)
        assertEquals(20, created.requestedMinutes)
        assertNull(created.resolvedMinutes)

        val pending = accessRequestService.listPendingForParent(parentId)
        assertEquals(1, pending.count { it.id == created.id })
    }

    @Test
    fun `requesting more time for an app with no catalog entry fails`() {
        val (_, childId, _) = newParentChildAndRuledApp()

        val ex = assertThrows(ApiException.NotFound::class.java) {
            accessRequestService.createRequest(childId, CreateAccessRequestRequest("com.example.never-ruled", requestedMinutes = 10))
        }
        assertEquals("APP_NOT_FOUND", ex.code)
    }

    @Test
    fun `approving a request sets resolvedMinutes and removes it from the pending list`() {
        val (parentId, childId, packageName) = newParentChildAndRuledApp()
        val created = accessRequestService.createRequest(childId, CreateAccessRequestRequest(packageName, requestedMinutes = 15))

        val resolved = accessRequestService.resolve(
            parentId,
            UUID.fromString(created.id),
            ResolveAccessRequestRequest(approve = true, resolvedMinutes = 10),
        )

        assertEquals(AccessRequestStatus.APPROVED, resolved.status)
        assertEquals(10, resolved.resolvedMinutes)
        assertTrueNoLongerPending(parentId, created.id)
    }

    @Test
    fun `approving without an explicit resolvedMinutes falls back to the requested amount`() {
        val (parentId, childId, packageName) = newParentChildAndRuledApp()
        val created = accessRequestService.createRequest(childId, CreateAccessRequestRequest(packageName, requestedMinutes = 25))

        val resolved = accessRequestService.resolve(parentId, UUID.fromString(created.id), ResolveAccessRequestRequest(approve = true))

        assertEquals(25, resolved.resolvedMinutes)
    }

    @Test
    fun `denying a request leaves resolvedMinutes null`() {
        val (parentId, childId, packageName) = newParentChildAndRuledApp()
        val created = accessRequestService.createRequest(childId, CreateAccessRequestRequest(packageName, requestedMinutes = 15))

        val resolved = accessRequestService.resolve(parentId, UUID.fromString(created.id), ResolveAccessRequestRequest(approve = false))

        assertEquals(AccessRequestStatus.DENIED, resolved.status)
        assertNull(resolved.resolvedMinutes)
    }

    @Test
    fun `resolving an already-resolved request throws Conflict, not a silent second write`() {
        val (parentId, childId, packageName) = newParentChildAndRuledApp()
        val created = accessRequestService.createRequest(childId, CreateAccessRequestRequest(packageName, requestedMinutes = 15))
        accessRequestService.resolve(parentId, UUID.fromString(created.id), ResolveAccessRequestRequest(approve = true))

        val ex = assertThrows(ApiException.Conflict::class.java) {
            accessRequestService.resolve(parentId, UUID.fromString(created.id), ResolveAccessRequestRequest(approve = false))
        }
        assertEquals("ACCESS_REQUEST_ALREADY_RESOLVED", ex.code)
    }

    @Test
    fun `a non-owning parent gets NOT_FOUND, never a leak of the request's existence`() {
        val (_, childId, packageName) = newParentChildAndRuledApp()
        val created = accessRequestService.createRequest(childId, CreateAccessRequestRequest(packageName, requestedMinutes = 15))

        val signup = authService.signup(SignupRequest("other-parent-${UUID.randomUUID()}@example.com", "password123", "Other"))
        val otherParentId = UUID.fromString(signup.parentId)

        val ex = assertThrows(ApiException.NotFound::class.java) {
            accessRequestService.resolve(otherParentId, UUID.fromString(created.id), ResolveAccessRequestRequest(approve = true))
        }
        assertEquals("ACCESS_REQUEST_NOT_FOUND", ex.code)
    }

    private fun assertTrueNoLongerPending(parentId: UUID, requestId: String) {
        assertEquals(0, accessRequestService.listPendingForParent(parentId).count { it.id == requestId })
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
