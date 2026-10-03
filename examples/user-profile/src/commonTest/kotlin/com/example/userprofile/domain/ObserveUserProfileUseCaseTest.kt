package com.example.userprofile.domain

import com.example.userprofile.fakes.FakeSettingsRepository
import com.example.userprofile.fakes.FakeUserRepository
import com.example.userprofile.fakes.user
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The highest-value test tier in a KMP codebase: pure logic over injected interfaces, in
 * commonTest, with no platform, no emulator, no mocking framework.
 *
 * `kotlin.test` only — JUnit annotations, Robolectric, MockK and Truth do not compile for
 * native targets. See references/kmp/project-structure.md § Testing recommendations.
 */
class ObserveUserProfileUseCaseTest {

    private val userRepository = FakeUserRepository()
    private val settingsRepository = FakeSettingsRepository()
    private val observeUserProfile = ObserveUserProfileUseCase(userRepository, settingsRepository)

    @Test
    fun emitsNullWhenNoUserIsCached() = runTest {
        assertNull(observeUserProfile().first())
    }

    @Test
    fun usesFullNameByDefault() = runTest {
        userRepository.emit(user(displayName = "Ada Lovelace"))

        assertEquals("Ada Lovelace", observeUserProfile().first()?.label)
    }

    @Test
    fun appliesInitialsFormatFromPreferences() = runTest {
        userRepository.emit(user(displayName = "Ada Lovelace"))
        settingsRepository.emit(DisplayPreferences(nameFormat = NameFormat.INITIALS))

        assertEquals("AL", observeUserProfile().first()?.label)
    }

    @Test
    fun hidesAvatarWhenPreferenceIsOff() = runTest {
        userRepository.emit(user(avatarUrl = "https://example.com/a.png"))
        settingsRepository.emit(DisplayPreferences(showAvatars = false))

        assertNull(observeUserProfile().first()?.avatarUrl)
    }

    // Boundary case. A single-word name has no second initial to take.
    @Test
    fun singleWordNameYieldsOneInitial() = runTest {
        userRepository.emit(user(displayName = "Ada"))
        settingsRepository.emit(DisplayPreferences(nameFormat = NameFormat.INITIALS))

        assertEquals("A", observeUserProfile().first()?.label)
    }

    // Boundary case: pinned deliberately. A blank display name produces "?" rather than an
    // empty label or a crash. See references/quality/testing-strategy.md § 4.3.
    @Test
    fun blankNameYieldsQuestionMarkRatherThanEmptyLabel() = runTest {
        userRepository.emit(user(displayName = "   "))
        settingsRepository.emit(DisplayPreferences(nameFormat = NameFormat.INITIALS))

        assertEquals("?", observeUserProfile().first()?.label)
    }
}
