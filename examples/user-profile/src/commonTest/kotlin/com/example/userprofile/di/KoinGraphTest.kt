package com.example.userprofile.di

import com.example.userprofile.data.UserEntity
import com.example.userprofile.data.UserLocalDataSource
import com.example.userprofile.domain.UserRepository
import com.example.userprofile.fakes.FakeSettingsRepository
import com.example.userprofile.network.AuthTokens
import com.example.userprofile.network.TokenRefresher
import com.example.userprofile.network.TokenStore
import com.example.userprofile.platform.DeviceInfo
import com.example.userprofile.presentation.UserViewModel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The graph check: every binding the shared modules declare can be resolved, given the
 * environment the host app must supply. A missing `get()` fails here in CI rather than at
 * first use on a device.
 *
 * Koin is a runtime container; this test is what stands in for compile-time validation.
 * See references/libraries/koin-di.md § The real trade-off.
 *
 * `koinApplication { }` builds an isolated container — no global `startKoin`, so tests
 * can run in parallel and nothing leaks between them.
 */
class KoinGraphTest {

    private class NoopLocal : UserLocalDataSource {
        private val rows = MutableStateFlow<UserEntity?>(null)
        override fun observeUser(): Flow<UserEntity?> = rows
        override suspend fun put(user: UserEntity) { rows.value = user }
    }
    private class NoopTokenStore : TokenStore {
        override suspend fun read(): AuthTokens? = null
        override suspend fun write(tokens: AuthTokens) = Unit
        override suspend fun clear() = Unit
    }
    private class NoopRefresher : TokenRefresher {
        override suspend fun refresh(refreshToken: String): AuthTokens? = null
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun app() = koinApplication {
        modules(
            sharedModules() + environmentModule(
                engine = MockEngine { respondOk() },
                baseUrl = "https://api.example.com/",
                ioDispatcher = UnconfinedTestDispatcher(),
                tokenStore = NoopTokenStore(),
                tokenRefresher = NoopRefresher(),
                localDataSource = NoopLocal(),
                settingsRepository = FakeSettingsRepository(),
            ),
        )
    }

    @Test
    fun everyPublicBindingResolves() {
        val koin = app().koin
        assertNotNull(koin.get<UserRepository>())
        assertNotNull(koin.get<UserViewModel>())
        assertNotNull(koin.get<DeviceInfo>())
    }

    @Test
    fun repositoryIsASingle() {
        val koin = app().koin
        kotlin.test.assertSame(koin.get<UserRepository>(), koin.get<UserRepository>())
    }
}
