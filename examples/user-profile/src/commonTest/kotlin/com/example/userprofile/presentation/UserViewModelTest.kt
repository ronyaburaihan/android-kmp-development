package com.example.userprofile.presentation

import app.cash.turbine.test
import com.example.userprofile.domain.ObserveUserProfileUseCase
import com.example.userprofile.domain.UserError
import com.example.userprofile.fakes.FakeSettingsRepository
import com.example.userprofile.fakes.FakeUserRepository
import com.example.userprofile.fakes.user
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `viewModelScope` is bound to `Dispatchers.Main`, which has no implementation in a test
 * on any target. `Dispatchers.setMain` installs one; omitting it fails with
 * "Module with the Main dispatcher had failed to initialize".
 *
 * Turbine is used for emission sequences; `uiState.value` is sufficient for a snapshot
 * assertion. Both are in references/kotlin/coroutines-and-flow.md § Testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserViewModelTest {

    private val userRepository = FakeUserRepository()
    private val settingsRepository = FakeSettingsRepository()

    private fun viewModel() = UserViewModel(
        observeUserProfile = ObserveUserProfileUseCase(userRepository, settingsRepository),
        userRepository = userRepository,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * `uiState` is built with `stateIn(..., WhileSubscribed(), ...)`, so the upstream does
     * not start until something collects. Reading `.value` without a collector sees the
     * initial value forever — a test that passes while asserting nothing.
     * See references/architecture/mvvm-udf.md § Testing recommendations.
     */
    @Test
    fun emitsProfileOnceUserIsAvailable() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertEquals(null, awaitItem().profile)

            userRepository.emit(user(displayName = "Ada Lovelace"))

            assertEquals("Ada Lovelace", awaitItem().profile?.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refreshDelegatesToTheRepository() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.refresh()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, userRepository.refreshCount)
    }

    @Test
    fun refreshFailureSurfacesAsAMessageAndClearsRefreshing() = runTest {
        userRepository.nextRefreshError = UserError.Offline
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.refresh()

            val state = expectMostRecentItem()
            assertEquals(
                UiText.Key(MessageKey.OFFLINE),
                state.messages.single().text,
            )
            assertFalse(state.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The acknowledgement half of the state-held message pattern. A message that is added
     * but never cleared is the characteristic bug of this approach, and it only shows up in
     * a test that performs the acknowledgement.
     */
    @Test
    fun messageIsClearedAfterAcknowledgement() = runTest {
        userRepository.nextRefreshError = UserError.Offline
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.refresh()
            val messageId = expectMostRecentItem().messages.single().id

            viewModel.onMessageShown(messageId)

            assertTrue(expectMostRecentItem().messages.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun eachErrorMapsToItsOwnMessage() = runTest {
        val cases = listOf(
            UserError.NotFound to MessageKey.USER_NOT_FOUND,
            UserError.Unauthorized to MessageKey.SESSION_EXPIRED,
            UserError.Offline to MessageKey.OFFLINE,
            UserError.Unexpected(RuntimeException("boom")) to MessageKey.USER_LOAD_FAILED,
        )

        for ((error, expected) in cases) {
            val repository = FakeUserRepository().apply { nextRefreshError = error }
            val viewModel = UserViewModel(
                observeUserProfile = ObserveUserProfileUseCase(repository, FakeSettingsRepository()),
                userRepository = repository,
            )

            viewModel.uiState.test {
                awaitItem()
                viewModel.refresh()
                assertEquals(
                    UiText.Key(expected),
                    expectMostRecentItem().messages.single().text,
                    "error $error should map to $expected",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
