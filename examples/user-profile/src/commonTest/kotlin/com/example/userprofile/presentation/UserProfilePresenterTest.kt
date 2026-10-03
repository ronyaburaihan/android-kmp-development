package com.example.userprofile.presentation

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.example.userprofile.domain.ObserveUserProfileUseCase
import com.example.userprofile.domain.UserError
import com.example.userprofile.fakes.FakeSettingsRepository
import com.example.userprofile.fakes.FakeUserRepository
import com.example.userprofile.fakes.user
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compare with UserViewModelTest: no `Dispatchers.setMain`, no `@BeforeTest`/`@AfterTest`.
 * The presenter takes the scope `runTest` provides (`backgroundScope`), which runs on a
 * `StandardTestDispatcher` under virtual time. Work launched by an event has not run yet
 * when `onEvent` returns; it runs when the test **suspends** — `awaitItem()` suspends, so
 * waiting for the next emission is also what lets the launched work execute. That explicit
 * control is the practical difference the presenter pattern buys in tests.
 * See references/architecture/presenter.md.
 */
class UserProfilePresenterTest {

    private val userRepository = FakeUserRepository()

    private fun TestScope.presenter() = UserProfilePresenter(
        scope = backgroundScope,
        observeUserProfile = ObserveUserProfileUseCase(userRepository, FakeSettingsRepository()),
        userRepository = userRepository,
    )

    /** Consumes emissions until one satisfies [predicate]; suspension drives the scheduler. */
    private suspend fun ReceiveTurbine<UserUiState>.awaitState(
        predicate: (UserUiState) -> Boolean,
    ): UserUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun emitsProfileOnceUserIsAvailable() = runTest {
        val presenter = presenter()
        presenter.state.test {
            assertEquals(null, awaitItem().profile)
            userRepository.emit(user(displayName = "Ada Lovelace"))
            assertEquals("Ada Lovelace", awaitItem().profile?.label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refreshFailureSurfacesAsMessageAndIsAcknowledgeable() = runTest {
        userRepository.nextRefreshError = UserError.Offline
        val presenter = presenter()
        presenter.state.test {
            awaitItem()
            presenter.onEvent(UserProfilePresenter.Event.Refresh)

            val withMessage = awaitState { it.messages.isNotEmpty() && !it.isRefreshing }
            assertEquals(UiText.Key(MessageKey.USER_LOAD_FAILED), withMessage.messages.single().text)

            presenter.onEvent(UserProfilePresenter.Event.MessageShown(withMessage.messages.single().id))
            assertTrue(awaitState { it.messages.isEmpty() }.messages.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
