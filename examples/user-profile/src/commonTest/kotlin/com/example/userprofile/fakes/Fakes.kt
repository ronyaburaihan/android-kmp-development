package com.example.userprofile.fakes

import com.example.userprofile.domain.DisplayPreferences
import com.example.userprofile.domain.SettingsRepository
import com.example.userprofile.domain.User
import com.example.userprofile.domain.UserError
import com.example.userprofile.domain.UserException
import com.example.userprofile.domain.UserId
import com.example.userprofile.domain.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fakes, not mocks.
 *
 * A fake is a real implementation with simplified behaviour: it exercises the contract,
 * survives refactoring, and does not encode call order. `verify(times(1))` asserts *how*
 * the subject works, so adding a legitimate cache check breaks a passing test with no
 * behaviour regression.
 *
 * There is a second, harder constraint in KMP: most mocking frameworks are JVM-only and
 * will not compile for native, so a mock-based test cannot live in commonTest at all.
 * See references/quality/testing-strategy.md § Fakes, not mocks.
 *
 * In a multi-module project these belong in commonMain of a shared testing module — one
 * module's commonTest is not visible to another.
 * See references/architecture/modularization.md § Testing recommendations.
 */
class FakeUserRepository : UserRepository {

    private val users = MutableStateFlow<User?>(null)

    var refreshCount: Int = 0
        private set

    /** Set to make the next [refresh] fail. */
    var nextRefreshError: UserError? = null

    override fun observeUser(): Flow<User?> = users

    override suspend fun refresh() {
        refreshCount++
        nextRefreshError?.let { error ->
            nextRefreshError = null
            throw UserException(error)
        }
    }

    fun emit(user: User?) {
        users.value = user
    }
}

class FakeSettingsRepository(
    initial: DisplayPreferences = DisplayPreferences(),
) : SettingsRepository {

    private val preferences = MutableStateFlow(initial)

    override fun observeDisplayPreferences(): Flow<DisplayPreferences> = preferences

    fun emit(value: DisplayPreferences) {
        preferences.value = value
    }
}

internal fun user(
    id: String = "u1",
    displayName: String = "Ada Lovelace",
    email: String = "ada@example.com",
    avatarUrl: String? = "https://example.com/a.png",
): User = User(
    id = UserId(id),
    displayName = displayName,
    email = email,
    avatarUrl = avatarUrl,
)
