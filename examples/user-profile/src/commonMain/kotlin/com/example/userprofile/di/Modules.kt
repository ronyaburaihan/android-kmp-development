package com.example.userprofile.di

import com.example.userprofile.data.DefaultUserRepository
import com.example.userprofile.data.UserLocalDataSource
import com.example.userprofile.data.UserRemoteDataSource
import com.example.userprofile.domain.ObserveUserProfileUseCase
import com.example.userprofile.domain.SettingsRepository
import com.example.userprofile.domain.UserRepository
import com.example.userprofile.network.KtorUserRemoteDataSource
import com.example.userprofile.network.TokenManager
import com.example.userprofile.network.TokenRefresher
import com.example.userprofile.network.TokenStore
import com.example.userprofile.network.createHttpClient
import com.example.userprofile.platform.DeviceInfo
import com.example.userprofile.platform.deviceInfo
import com.example.userprofile.presentation.UserViewModel
import com.example.userprofile.session.SessionEventBus
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Scope rules — the one Koin decision that produces bugs when it is wrong:
 *
 *  - `single`  → holds shared mutable state or is expensive to build: the HTTP client
 *                (connection pool), repositories (caches), the event bus, the token manager.
 *  - `factory` → stateless: use cases, mappers.
 *  - ViewModels → `viewModelOf` from koin-compose-viewmodel in a Compose app; here the
 *                ViewModel is registered with `factory` only so the graph can be verified
 *                without the Compose artifacts. A `single` ViewModel is shared across every
 *                screen instance and never cleared.
 *
 * See references/libraries/koin-di.md § Scoping.
 *
 * What is deliberately **not** bound here: `UserLocalDataSource`, `TokenStore`,
 * `TokenRefresher`, `HttpClientEngine`, the IO dispatcher. Those are platform or
 * environment concerns and come from [platformModule] — the pattern that keeps
 * `android.content.Context` out of common code.
 */
public val domainModule: Module = module {
    factory { ObserveUserProfileUseCase(get(), get()) }
}

public val dataModule: Module = module {
    single { SessionEventBus() }
    single { TokenManager(get(), get(), get()) }
    single { createHttpClient(engine = get(), baseUrl = get(named("baseUrl")), tokens = get()) }
    single<UserRemoteDataSource> { KtorUserRemoteDataSource(get()) }
    single<UserRepository> {
        DefaultUserRepository(remote = get(), local = get(), ioDispatcher = get(named("io")))
    }
}

public val presentationModule: Module = module {
    factory { UserViewModel(observeUserProfile = get(), userRepository = get()) }
}

/**
 * Supplied per platform. Declared as an `expect fun` returning a Koin [Module] rather than
 * an `expect class`, and the platform-specific types it binds — `DeviceInfo` here — are
 * reached only through their common interfaces.
 */
public expect fun platformModule(): Module

/**
 * Environment bindings: engine, base URL, dispatcher, storage.
 *
 * `internal`, because it takes data-layer types (`UserLocalDataSource`) that are not part
 * of the module's API — the compiler enforces this under `explicitApi()`:
 * "'public' function exposes its 'internal' parameter type". In a real multi-module
 * project the local data source is bound inside the data module and this function does
 * not exist; it is parameterised here so the graph can be verified in commonTest with a
 * `MockEngine` and in-memory stores.
 */
internal fun environmentModule(
    engine: HttpClientEngine,
    baseUrl: String,
    ioDispatcher: CoroutineDispatcher,
    tokenStore: TokenStore,
    tokenRefresher: TokenRefresher,
    localDataSource: UserLocalDataSource,
    settingsRepository: SettingsRepository,
): Module = module {
    single<HttpClientEngine> { engine }
    single(named("baseUrl")) { baseUrl }
    single<CoroutineDispatcher>(named("io")) { ioDispatcher }
    single<TokenStore> { tokenStore }
    single<TokenRefresher> { tokenRefresher }
    single<UserLocalDataSource> { localDataSource }
    single<SettingsRepository> { settingsRepository }
}

public fun sharedModules(): List<Module> = listOf(domainModule, dataModule, presentationModule, platformModule())

internal fun commonPlatformBindings(): Module = module {
    single<DeviceInfo> { deviceInfo() }
}
