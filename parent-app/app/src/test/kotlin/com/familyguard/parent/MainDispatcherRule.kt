package com.familyguard.parent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Routes `viewModelScope` (which defaults to `Dispatchers.Main`) onto a test dispatcher for
 * the duration of each test, per the standard `kotlinx-coroutines-test` ViewModel-testing
 * pattern referenced in docs/testing-plan.md.
 *
 * Defaults to [UnconfinedTestDispatcher] (not `StandardTestDispatcher`): it runs coroutines
 * eagerly on the thread that launched them, so a ViewModel's `viewModelScope.launch { ... }`
 * body executes immediately rather than needing to be pumped by a scheduler shared with the
 * test's own `runTest { }` — the tests in this module call `advanceUntilIdle()` from
 * `runTest`'s default (standard) scheduler, which is a *different* scheduler instance than
 * one owned by a fresh `StandardTestDispatcher()`, so that combination would silently never
 * drain the ViewModel's coroutines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
