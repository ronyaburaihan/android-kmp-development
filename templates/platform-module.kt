// Template: the Koin platform-module pattern. See references/libraries/koin-di.md.
// Keeps android.content.Context out of commonMain; prefers interfaces over expect classes.

// ---- commonMain/.../di/PlatformModule.kt ----
package <pkg>.di

import org.koin.core.module.Module

/** Supplied per platform. An `expect fun` returning a Module — never an `expect class`. */
public expect fun platformModule(): Module

// ---- androidMain/.../di/PlatformModule.android.kt ----
// package <pkg>.di
// import org.koin.android.ext.koin.androidContext
// import org.koin.dsl.module
// public actual fun platformModule(): Module = module {
//     single<HttpClientEngine> { OkHttp.create() }
//     single<AppPaths> { AndroidAppPaths(androidContext()) }     // Context stays here
//     single { getDatabaseBuilder(androidContext()) }
// }

// ---- iosMain/.../di/PlatformModule.ios.kt ----
// package <pkg>.di
// import org.koin.dsl.module
// public actual fun platformModule(): Module = module {
//     single<HttpClientEngine> { Darwin.create() }
//     single<AppPaths> { IosAppPaths() }
//     single { getDatabaseBuilder() }
// }

// ---- iosMain/.../di/SharedDependencies.kt ----
// Swift cannot call reified get<T>(); expose concrete accessors. The only place KoinComponent is acceptable.
// public class SharedDependencies : KoinComponent {
//     public val sharedApi: SharedApi get() = get()
// }
