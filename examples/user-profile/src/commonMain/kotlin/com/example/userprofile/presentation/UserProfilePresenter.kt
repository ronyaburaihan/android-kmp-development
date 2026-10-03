package com.example.userprofile.presentation

import com.example.userprofile.domain.ObserveUserProfileUseCase
import com.example.userprofile.domain.UserException
import com.example.userprofile.domain.UserProfile
import com.example.userprofile.domain.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The presenter alternative to `UserViewModel`: same contract (one immutable state, events
 * in), **no lifecycle framework**. The caller owns the [CoroutineScope], which is the whole
 * difference.
 *
 * Choose a presenter over a ViewModel when:
 *  - the UI is native SwiftUI over shared code: iOS has no `ViewModelStoreOwner`, so a
 *    ViewModel needs a third-party bridge, whereas a presenter is a plain object the Swift
 *    side can own and cancel;
 *  - a screen is composed of independently-scoped parts (a Circuit-style architecture);
 *  - tests should run with no `Dispatchers.Main` substitution — `viewModelScope` requires
 *    `Dispatchers.setMain`, a scope parameter does not.
 *
 * Choose a ViewModel when the host is Android/CMP and you want survival across
 * configuration change for free. See references/architecture/presenter.md.
 *
 * The state and event types are shared with `UserViewModel` on purpose: the UI does not
 * know or care which produced them.
 */
public class UserProfilePresenter(
    private val scope: CoroutineScope,
    observeUserProfile: ObserveUserProfileUseCase,
    private val userRepository: UserRepository,
) {
    public sealed interface Event {
        public data object Refresh : Event
        public data class MessageShown(val id: Long) : Event
    }

    private val transient = MutableStateFlow(Transient())

    public val state: StateFlow<UserUiState> =
        combine(observeUserProfile(), transient) { profile: UserProfile?, t: Transient ->
            UserUiState(profile = profile, isRefreshing = t.isRefreshing, messages = t.messages)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), UserUiState())

    public fun onEvent(event: Event) {
        when (event) {
            Event.Refresh -> refresh()
            is Event.MessageShown -> transient.update { s ->
                s.copy(messages = s.messages.filterNot { it.id == event.id })
            }
        }
    }

    private fun refresh() {
        scope.launch {
            transient.update { it.copy(isRefreshing = true) }
            try {
                userRepository.refresh()
                transient.update { it.withMessage(UiText.Key(MessageKey.PROFILE_REFRESHED)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserException) {
                transient.update { it.withMessage(UiText.Key(MessageKey.USER_LOAD_FAILED)) }
            } finally {
                transient.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private data class Transient(
        val isRefreshing: Boolean = false,
        val messages: List<UserMessage> = emptyList(),
        val nextId: Long = 0,
    ) {
        fun withMessage(text: UiText) =
            copy(messages = messages + UserMessage(nextId, text), nextId = nextId + 1)
    }
}
