package com.familyguard.child.ui.pairing

import com.familyguard.child.data.repository.PairingRepository
import com.familyguard.child.work.WorkScheduler
import com.familyguard.shared.enums.DeviceStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {

    private lateinit var pairingRepository: PairingRepository
    private lateinit var workScheduler: WorkScheduler
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        pairingRepository = mockk()
        workScheduler = mockk(relaxed = true)
        every { pairingRepository.isPaired() } returns false
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PairingViewModel(pairingRepository, workScheduler)

    @Test
    fun `starts in EnteringCode when not already paired`() {
        val vm = viewModel()
        assertEquals(PairingUiState.EnteringCode, vm.uiState.value)
    }

    @Test
    fun `starts in Paired when already paired`() {
        every { pairingRepository.isPaired() } returns true
        val vm = viewModel()
        assertEquals(PairingUiState.Paired, vm.uiState.value)
    }

    @Test
    fun `submitCode with an unparseable value fails without calling claim`() {
        every { pairingRepository.extractCode(any()) } returns null
        val vm = viewModel()

        vm.submitCode("not a code")

        assertTrue(vm.uiState.value is PairingUiState.ClaimFailed)
    }

    @Test
    fun `submitCode with a valid code transitions to WaitingForApproval on claim success`() {
        every { pairingRepository.extractCode("AB3D9K2M") } returns "AB3D9K2M"
        coEvery { pairingRepository.claim("AB3D9K2M") } returns Result.success("device-1")
        val vm = viewModel()

        vm.submitCode("AB3D9K2M")

        assertEquals(PairingUiState.WaitingForApproval, vm.uiState.value)
    }

    @Test
    fun `submitCode surfaces a claim failure`() {
        every { pairingRepository.extractCode("AB3D9K2M") } returns "AB3D9K2M"
        coEvery { pairingRepository.claim("AB3D9K2M") } returns Result.failure(RuntimeException("code expired"))
        val vm = viewModel()

        vm.submitCode("AB3D9K2M")

        assertTrue(vm.uiState.value is PairingUiState.ClaimFailed)
    }

    @Test
    fun `pollUntilResolved moves to Paired and schedules work once approved and tokens exchanged`() = runTest(dispatcher) {
        coEvery { pairingRepository.pollStatus() } returns Result.success(DeviceStatus.APPROVED)
        coEvery { pairingRepository.exchangeToken() } returns Result.success(Unit)
        val vm = viewModel()
        var callbackFired = false

        launch { vm.pollUntilResolved { callbackFired = true } }

        assertEquals(PairingUiState.Paired, vm.uiState.value)
        assertTrue(callbackFired)
    }

    @Test
    fun `pollUntilResolved surfaces Revoked and stops polling`() = runTest(dispatcher) {
        coEvery { pairingRepository.pollStatus() } returns Result.success(DeviceStatus.REVOKED)
        val vm = viewModel()

        launch { vm.pollUntilResolved { } }

        assertEquals(PairingUiState.Revoked, vm.uiState.value)
    }
}
