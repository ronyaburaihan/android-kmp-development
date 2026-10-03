package com.example.userprofile.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines two independent streams and applies a display rule neither repository owns.
 *
 * This is what justifies a use case. A class whose body is `return repository.getUser()`
 * adds a file, a DI binding, and a test that can only assert delegation — the domain layer
 * is "recommended in big apps", not mandatory, and a pass-through obscures the real use
 * cases by sitting among them.
 * See references/architecture/clean-architecture.md § Use cases.
 *
 * `operator fun invoke` so call sites read `observeUserProfile()`.
 */
public class ObserveUserProfileUseCase(
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
) {
    public operator fun invoke(): Flow<UserProfile?> =
        combine(
            userRepository.observeUser(),
            settingsRepository.observeDisplayPreferences(),
        ) { user, preferences ->
            user?.toProfile(preferences)
        }
}

internal fun User.toProfile(preferences: DisplayPreferences): UserProfile =
    UserProfile(
        id = id,
        label = when (preferences.nameFormat) {
            NameFormat.FULL -> displayName
            NameFormat.INITIALS -> displayName.toInitials()
        },
        email = email,
        avatarUrl = avatarUrl.takeIf { preferences.showAvatars },
    )

private fun String.toInitials(): String =
    split(' ')
        .filter { it.isNotBlank() }
        .joinToString(separator = "") { it.first().uppercase() }
        .ifEmpty { "?" }
