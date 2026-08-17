package com.familyguard.parent.ui.pairing

import com.familyguard.parent.MainDispatcherRule
import com.familyguard.parent.data.repository.PairingRepository
import com.familyguard.shared.dto.DeviceResponse
import com.familyguard.shared.dto.PairingCodeResponse
import com.familyguard.shared.enums.DeviceStatus
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakePairingRepository : PairingRepository {
    var codeResult: Result<PairingCodeResponse> = Result.success(PairingCodeResponse("ABCD1234", "2026-08-16T12:10:00Z"))
    var pendingDevice: DeviceResponse? = null
    var findPendingCallCount = 0

    override suspend fun generateCode(childId: String): Result<PairingCodeResponse> = codeResult

    override suspend fun getDevices(childId: String): Result<List<DeviceResponse>> =
        Result.success(listOfNotNull(pendingDevice))

    override suspend fun findPendingApprovalDevice(childId: String): Result<DeviceResponse?> {
        findPendingCallCount++
        return Result.success(pendingDevice)
    }

    override suspend fun approveDevice(deviceId: String): Result<DeviceResponse> =
        Result.success(sampleDevice(deviceId, DeviceStatus.APPROVED))

    override suspend fun revokeDevice(deviceId: String): Result<Unit> = Result.success(Unit)
}

private fun sampleDevice(id: String, status: DeviceStatus) = DeviceResponse(
    id = id,
    childId = "child-1",
    deviceName = "Pixel",
    model = "Pixel 8",
    osVersion = "15",
    appVersion = "1.0",
    status = status,
    lastSeenAt = null,
    lastSyncAt = null,
    pairedAt = "2026-08-16T12:00:00Z",
    approvedAt = null,
)

class PairingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `generateCode succeeds and produces CodeReady`() = runTest {
        val repository = FakePairingRepository()
        val viewModel = PairingViewModel(repository)

        viewModel.generateCode("child-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PairingUiState.CodeReady)
        assertEquals("ABCD1234", (state as PairingUiState.CodeReady).code)
    }

    @Test
    fun `polling before a device appears leaves state as CodeReady`() = runTest {
        val repository = FakePairingRepository()
        val viewModel = PairingViewModel(repository)
        viewModel.generateCode("child-1")
        advanceUntilIdle()

        viewModel.pollForDevice("child-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PairingUiState.CodeReady)
        assertEquals(1, repository.findPendingCallCount)
    }

    @Test
    fun `polling transitions to DeviceFound once a pending device appears`() = runTest {
        val repository = FakePairingRepository()
        val viewModel = PairingViewModel(repository)
        viewModel.generateCode("child-1")
        advanceUntilIdle()

        // First poll: nothing yet.
        viewModel.pollForDevice("child-1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PairingUiState.CodeReady)

        // Child device claims the code between polls.
        repository.pendingDevice = sampleDevice("device-1", DeviceStatus.PENDING_APPROVAL)

        viewModel.pollForDevice("child-1")
        advanceUntilIdle()

        assertEquals(PairingUiState.DeviceFound, viewModel.uiState.value)
        assertEquals(2, repository.findPendingCallCount)
    }

    @Test
    fun `polling before a code exists is a no-op`() = runTest {
        val repository = FakePairingRepository()
        val viewModel = PairingViewModel(repository)
        // No generateCode() call yet — ViewModel starts in Loading.

        viewModel.pollForDevice("child-1")
        advanceUntilIdle()

        assertEquals(PairingUiState.Loading, viewModel.uiState.value)
        assertEquals(0, repository.findPendingCallCount)
    }
}
