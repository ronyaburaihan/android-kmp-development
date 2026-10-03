package com.example.userprofile.api

import com.example.userprofile.di.environmentModule
import com.example.userprofile.di.sharedModules
import com.example.userprofile.domain.ObserveUserProfileUseCase
import com.example.userprofile.domain.UserException
import com.example.userprofile.domain.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

/**
 * The Swift-facing surface. Everything Swift can call is here, and it is designed for the
 * Objective-C export path — the production path today (Swift export is Alpha):
 *
 *  - no `Flow` in any signature: [observeProfile] takes a callback and returns a handle;
 *  - no generics, no default arguments, no inline value classes in signatures;
 *  - every throwing function carries `@Throws`, including `CancellationException` for suspend
 *    functions — an undeclared exception reaching Swift terminates the app;
 *  - plain `data class` DTOs (`ProfileSnapshot`) with primitive fields, not domain types.
 *
 * See references/kmp/ios-interop.md. `swift-smoke/SharedApiSmoke.swift` compiles against the
 * generated header to prove the surface is callable.
 */
// Objective-C names carry the framework prefix (`UserProfileSharedApi`); the generated header
// adds `swift_name("SharedApi")`, so Swift uses the unprefixed Kotlin name — verified against
// the header. `@ObjCName` can override both but requires `@OptIn(ExperimentalObjCName::class)`.
public class SharedApi internal constructor(
    private val observeUserProfile: ObserveUserProfileUseCase,
    private val userRepository: UserRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Current profile, or null when none is cached. */
    @Throws(CancellationException::class)
    public suspend fun currentProfile(): ProfileSnapshot? =
        observeUserProfile().first()?.let(::ProfileSnapshot)

    /** Refreshes from the network. Domain failures surface as [SharedApiException]. */
    @Throws(SharedApiException::class, CancellationException::class)
    public suspend fun refresh() {
        try {
            userRepository.refresh()
        } catch (e: UserException) {
            throw SharedApiException(e.error.toString(), e)
        }
    }

    /** Callback subscription — the Objective-C-safe shape of a Flow. Cancel via the handle. */
    public fun observeProfile(onChange: (ProfileSnapshot?) -> Unit): Cancellable {
        val job: Job = scope.launch {
            observeUserProfile().collect { onChange(it?.let(::ProfileSnapshot)) }
        }
        return Cancellable { job.cancel() }
    }

    public fun close() {
        scope.cancel()
    }
}

public class SharedApiException(message: String, cause: Throwable?) : Exception(message, cause)

public class Cancellable internal constructor(private val onCancel: () -> Unit) {
    public fun cancel(): Unit = onCancel()
}

/** Primitive-field snapshot: what Swift renders. Domain types stay inside the module. */
public data class ProfileSnapshot(
    public val id: String,
    public val label: String,
    public val email: String,
    public val avatarUrl: String?,
) {
    internal constructor(p: com.example.userprofile.domain.UserProfile) :
        this(p.id.value, p.label, p.email, p.avatarUrl)
}

/**
 * Swift cannot call Koin's reified `get<T>()`; this is the one `KoinComponent` entry point.
 * The host app supplies the environment (engine, base URL, stores) through [initSharedApi].
 */
public object SharedApiFactory : KoinComponent {
    public fun create(): SharedApi = SharedApi(get(), get())
}
