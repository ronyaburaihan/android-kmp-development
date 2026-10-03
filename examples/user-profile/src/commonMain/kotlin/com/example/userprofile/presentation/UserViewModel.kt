package com.example.userprofile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.userprofile.domain.ObserveUserProfileUseCase
import com.example.userprofile.domain.UserError
import com.example.userprofile.domain.UserException
import com.example.userprofile.domain.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared, not Android-specific.
 *
 * `androidx.lifecycle.ViewModel` here is the JetBrains multiplatform build
 * (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`), which keeps the AndroidX
 * package name. This file compiles for JVM and both iOS targets — the test suite proves it.
 *
 * Two consequences worth knowing before copying this:
 *
 *  - On non-JVM targets `viewModel()` cannot be called without an initializer, because
 *    there is no type reflection. Use `viewModel { UserViewModel(...) }` or the DI
 *    accessor (`koinViewModel()`).
 *  - With a native SwiftUI UI there is no `ViewModelStoreOwner` on iOS at all; a
 *    third-party observability bridge is needed.
 *
 * See references/kmp/compose-multiplatform.md § ViewModel.
 *
 * Desktop/JVM additionally needs `kotlinx-coroutines-swing`, because `viewModelScope` uses
 * `Dispatchers.Main.immediate`. This module's `jvm()` target does not run a UI, so it is
 * not declared here.
 */
public class UserViewModel(
    private val observeUserProfile: ObserveUserProfileUseCase,
    private val userRepository: UserRepository,
) : ViewModel() {

    // Mutable state is private; only the immutable projection is exposed, so there is
    // exactly one writer. See references/kotlin/coroutines-and-flow.md § 4.
    private val transient = MutableStateFlow(TransientState())

    /**
     * Explicit type on a public declaration: an inferred type means an unrelated
     * implementation change can silently alter this module's binary API — and, in a KMP
     * module, the generated Objective-C header.
     * See references/kotlin/coding-conventions.md § Public API surface rules.
     *
     * `WhileSubscribed(5_000)` keeps the upstream alive across a configuration change
     * without keeping it alive while the app is backgrounded. The 5-second figure is the
     * value in Google's own recommendation.
     * See references/android/app-architecture.md § ViewModel.
     */
    public val uiState: StateFlow<UserUiState> =
        combine(observeUserProfile(), transient) { profile, t ->
            UserUiState(
                profile = profile,
                isRefreshing = t.isRefreshing,
                messages = t.messages,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserUiState(),
        )

    /**
     * The ViewModel creates the coroutine; it does not expose a suspend function to the UI.
     * Exposing `suspend fun refresh()` would make the UI own the scope and cancellation —
     * and on iOS it would export as a completion handler with no real cancellation.
     * See references/kotlin/coroutines-and-flow.md § 3.
     */
    public fun refresh() {
        viewModelScope.launch {
            transient.update { it.copy(isRefreshing = true) }
            try {
                userRepository.refresh()
                transient.update { it.withMessage(UiText.Key(MessageKey.PROFILE_REFRESHED)) }
            } catch (e: CancellationException) {
                // Rethrow: cancellation is not a failure and must reach the parent.
                throw e
            } catch (e: UserException) {
                transient.update { it.withMessage(UiText.Key(e.error.toMessageKey())) }
            } finally {
                transient.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /**
     * The acknowledgement half of the state-held message pattern.
     *
     * The alternative — a `Channel` of events from the ViewModel to the UI — drops or
     * duplicates messages across configuration change, process death, and back-stack pops,
     * because delivery depends on a collector being active at emit time. Google marks
     * "do not send events from the ViewModel to the UI" as strongly recommended.
     * See references/architecture/mvvm-udf.md § One-off events.
     *
     * A message that is added but never cleared is the characteristic bug of this pattern,
     * so `UserViewModelTest` covers the acknowledgement explicitly.
     */
    public fun onMessageShown(messageId: Long) {
        transient.update { state ->
            state.copy(messages = state.messages.filterNot { it.id == messageId })
        }
    }

    private data class TransientState(
        val isRefreshing: Boolean = false,
        val messages: List<UserMessage> = emptyList(),
        val nextMessageId: Long = 0,
    ) {
        fun withMessage(text: UiText): TransientState =
            copy(
                messages = messages + UserMessage(id = nextMessageId, text = text),
                nextMessageId = nextMessageId + 1,
            )
    }
}

/**
 * Error-to-message mapping lives in the presentation layer: the domain decides *what*
 * failed, the presentation decides *what to say*. A `Throwable` in `UiState` would push
 * that decision into the composable.
 */
private fun UserError.toMessageKey(): MessageKey = when (this) {
    UserError.NotFound -> MessageKey.USER_NOT_FOUND
    UserError.Unauthorized -> MessageKey.SESSION_EXPIRED
    UserError.Offline -> MessageKey.OFFLINE
    is UserError.Unexpected -> MessageKey.USER_LOAD_FAILED
}
