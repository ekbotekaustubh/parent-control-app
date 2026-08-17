package com.familyguard.parent.ui.accessrequests

import com.familyguard.parent.MainDispatcherRule
import com.familyguard.parent.data.remote.ApiException
import com.familyguard.parent.data.repository.AccessRequestRepository
import com.familyguard.shared.dto.AccessRequestResponse
import com.familyguard.shared.enums.AccessRequestStatus
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeAccessRequestRepository(
    private var pending: List<AccessRequestResponse> = emptyList(),
) : AccessRequestRepository {
    var lastResolve: Pair<String, Boolean>? = null
    var resolveResult: Result<AccessRequestResponse>? = null

    override suspend fun getPendingRequests(): Result<List<AccessRequestResponse>> = Result.success(pending)

    override suspend fun resolve(requestId: String, approve: Boolean, resolvedMinutes: Int?): Result<AccessRequestResponse> {
        lastResolve = requestId to approve
        return resolveResult ?: Result.success(
            AccessRequestResponse(
                id = requestId,
                childId = "child-1",
                packageName = "com.example.app",
                displayName = "Example",
                requestedMinutes = 15,
                status = if (approve) AccessRequestStatus.APPROVED else AccessRequestStatus.DENIED,
                resolvedMinutes = if (approve) 15 else null,
                createdAt = "2026-08-17T00:00:00Z",
                resolvedAt = "2026-08-17T00:01:00Z",
            ),
        )
    }
}

private fun sampleRequest(id: String) = AccessRequestResponse(
    id = id,
    childId = "child-1",
    packageName = "com.example.app",
    displayName = "Example",
    requestedMinutes = 15,
    status = AccessRequestStatus.PENDING,
    resolvedMinutes = null,
    createdAt = "2026-08-17T00:00:00Z",
    resolvedAt = null,
)

class AccessRequestsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads into Empty when there are no pending requests`() = runTest {
        val viewModel = AccessRequestsViewModel(FakeAccessRequestRepository(emptyList()))
        advanceUntilIdle()

        assertEquals(AccessRequestsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `loads into Content when requests are pending`() = runTest {
        val viewModel = AccessRequestsViewModel(FakeAccessRequestRepository(listOf(sampleRequest("req-1"))))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AccessRequestsUiState.Content)
        assertEquals(1, (state as AccessRequestsUiState.Content).requests.size)
    }

    @Test
    fun `approving a request calls resolve with approve=true and removes it from the list`() = runTest {
        val repository = FakeAccessRequestRepository(listOf(sampleRequest("req-1")))
        val viewModel = AccessRequestsViewModel(repository)
        advanceUntilIdle()

        viewModel.approve("req-1")
        advanceUntilIdle()

        assertEquals("req-1" to true, repository.lastResolve)
        assertEquals(AccessRequestsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `denying a request calls resolve with approve=false`() = runTest {
        val repository = FakeAccessRequestRepository(listOf(sampleRequest("req-1")))
        val viewModel = AccessRequestsViewModel(repository)
        advanceUntilIdle()

        viewModel.deny("req-1")
        advanceUntilIdle()

        assertEquals("req-1" to false, repository.lastResolve)
    }

    @Test
    fun `a failed resolve surfaces an Error state`() = runTest {
        val repository = FakeAccessRequestRepository(listOf(sampleRequest("req-1")))
        repository.resolveResult = Result.failure(ApiException("Couldn't resolve this request."))
        val viewModel = AccessRequestsViewModel(repository)
        advanceUntilIdle()

        viewModel.approve("req-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AccessRequestsUiState.Error)
        assertEquals("Couldn't resolve this request.", (state as AccessRequestsUiState.Error).message)
    }

    @Test
    fun `resolving one request among several only removes that one`() = runTest {
        val repository = FakeAccessRequestRepository(listOf(sampleRequest("req-1"), sampleRequest("req-2")))
        val viewModel = AccessRequestsViewModel(repository)
        advanceUntilIdle()

        viewModel.approve("req-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AccessRequestsUiState.Content)
        assertEquals(listOf("req-2"), (state as AccessRequestsUiState.Content).requests.map { it.id })
    }
}
