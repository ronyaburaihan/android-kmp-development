package com.example.userprofile.presentation

import com.example.userprofile.domain.UserProfile

/**
 * One state object per screen, so every emission is a consistent snapshot.
 *
 * Three separate flows — `profile`, `isLoading`, `error` — emit independently, which lets
 * the UI render "loading" and a populated profile and an error at the same time.
 * See references/architecture/mvvm-udf.md § UI state modelling.
 *
 * A data class rather than a sealed interface, because the states are not mutually
 * exclusive here: the screen keeps showing a cached profile while refreshing. A sealed
 * hierarchy whose branches all have to carry `profile` is the signal to use this shape.
 *
 * Compose stability: every property is a `val` of a stable type, so this class is stable
 * and composables taking it can skip recomposition.
 * See references/android/compose-ui.md § Stability.
 */
public data class UserUiState(
    val profile: UserProfile? = null,
    val isRefreshing: Boolean = false,
    val messages: List<UserMessage> = emptyList(),
) {
    /** Derived, not stored: one fewer field that can disagree with the others. */
    public val isInitialLoad: Boolean get() = profile == null && isRefreshing
}

/**
 * A one-off outcome, held in state until the UI acknowledges it.
 *
 * The `id` exists so the UI can acknowledge a specific message. Without it, acknowledging
 * races with a second message arriving.
 */
public data class UserMessage(
    val id: Long,
    val text: UiText,
)
