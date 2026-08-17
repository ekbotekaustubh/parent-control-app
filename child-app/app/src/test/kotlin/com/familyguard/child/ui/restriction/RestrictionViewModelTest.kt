package com.familyguard.child.ui.restriction

import com.familyguard.child.data.repository.AccessRequestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestrictionViewModelTest {

    private lateinit var accessRequestRepository: AccessRequestRepository
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        accessRequestRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = RestrictionViewModel(accessRequestRepository)

    @Test
    fun `starts Idle`() {
        val vm = viewModel()
        assertEquals(RequestMoreTimeState.Idle, vm.requestState.value)
    }

    @Test
    fun `requestMoreTime moves to Sent on success, using the fixed minute increment`() {
        coEvery {
            accessRequestRepository.requestMoreTime("com.example.app", RestrictionViewModel.REQUESTED_MINUTES)
        } returns Result.success(Unit)
        val vm = viewModel()

        vm.requestMoreTime("com.example.app")

        assertEquals(RequestMoreTimeState.Sent, vm.requestState.value)
    }

    @Test
    fun `requestMoreTime surfaces a Failed state on repository failure`() {
        coEvery {
            accessRequestRepository.requestMoreTime(any(), any())
        } returns Result.failure(RuntimeException("network down"))
        val vm = viewModel()

        vm.requestMoreTime("com.example.app")

        assertTrue(vm.requestState.value is RequestMoreTimeState.Failed)
    }

    @Test
    fun `a second call while already Submitting is ignored`() {
        coEvery { accessRequestRepository.requestMoreTime(any(), any()) } coAnswers {
            // Never resolves within this test - simulates being mid-flight.
            suspendCancellableCoroutine { }
        }
        val vm = viewModel()

        vm.requestMoreTime("com.example.app")
        vm.requestMoreTime("com.example.app")

        coVerify(exactly = 1) { accessRequestRepository.requestMoreTime(any(), any()) }
    }
}
