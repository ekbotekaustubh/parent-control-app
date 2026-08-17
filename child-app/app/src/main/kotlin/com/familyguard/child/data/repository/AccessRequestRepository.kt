package com.familyguard.child.data.repository

import com.familyguard.child.data.remote.ChildApiService
import com.familyguard.shared.dto.CreateAccessRequestRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files a "more time please" request against the currently-restricted app (see
 * `ui/restriction/RestrictionScreen.kt`). Unlike [RuleRepository]/[UsageRepository], this is
 * a direct network call with no local cache/queue — it's a one-off ask made while the child
 * has connectivity (they're looking at a restriction screen right now), not something that
 * needs to survive being offline.
 */
@Singleton
class AccessRequestRepository @Inject constructor(
    private val api: ChildApiService,
) {
    suspend fun requestMoreTime(packageName: String, requestedMinutes: Int): Result<Unit> = runCatching {
        api.createAccessRequest(CreateAccessRequestRequest(packageName, requestedMinutes))
        Unit
    }
}
