package com.familyguard.parent.ui.auth

import com.familyguard.parent.MainDispatcherRule
import com.familyguard.parent.data.remote.ApiException
import com.familyguard.parent.data.repository.AuthRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeAuthRepository(
    private val loginResult: Result<Unit> = Result.success(Unit),
    private val signupResult: Result<Unit> = Result.success(Unit),
) : AuthRepository {
    var loggedIn = false
    var loginCallCount = 0
    var signupCallCount = 0

    override suspend fun signup(email: String, password: String, displayName: String): Result<Unit> {
        signupCallCount++
        if (signupResult.isSuccess) loggedIn = true
        return signupResult
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        loginCallCount++
        if (loginResult.isSuccess) loggedIn = true
        return loginResult
    }

    override suspend fun logout(): Result<Unit> {
        loggedIn = false
        return Result.success(Unit)
    }

    override fun isLoggedIn(): Boolean = loggedIn
}

class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `blank fields produce an error without calling the repository`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(0, repository.loginCallCount)
        assertTrue(viewModel.uiState.value is AuthUiState.Error)
    }

    @Test
    fun `successful login transitions Loading then Success`() = runTest {
        val repository = FakeAuthRepository(loginResult = Result.success(Unit))
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChange("parent@example.com")
        viewModel.onPasswordChange("hunter2")

        viewModel.submit()
        assertEquals(AuthUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(AuthUiState.Success, viewModel.uiState.value)
        assertEquals(1, repository.loginCallCount)
        assertTrue(repository.loggedIn)
    }

    @Test
    fun `failed login surfaces the repository's error message`() = runTest {
        val repository = FakeAuthRepository(
            loginResult = Result.failure(ApiException("Email or password is incorrect.")),
        )
        val viewModel = AuthViewModel(repository)
        viewModel.onEmailChange("parent@example.com")
        viewModel.onPasswordChange("wrong-password")

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Email or password is incorrect.", (state as AuthUiState.Error).message)
    }

    @Test
    fun `signup mode calls signup, not login, and requires a display name`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)
        viewModel.toggleMode()
        viewModel.onEmailChange("parent@example.com")
        viewModel.onPasswordChange("hunter2")

        // Missing display name in SIGNUP mode should error out before calling the repository.
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(0, repository.signupCallCount)
        assertTrue(viewModel.uiState.value is AuthUiState.Error)

        viewModel.onDisplayNameChange("Kaustubh")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.signupCallCount)
        assertEquals(0, repository.loginCallCount)
        assertEquals(AuthUiState.Success, viewModel.uiState.value)
    }
}
