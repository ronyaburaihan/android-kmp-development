package com.example.userprofile.alternatives

import com.example.userprofile.domain.User
import com.example.userprofile.domain.UserError
import kotlinx.coroutines.CancellationException

/**
 * The `Result`-returning alternative to typed exceptions, compiled so it is verified too.
 *
 * Use this file's shape when the codebase already standardised on a result wrapper. The
 * codebase outranks the default — see examples/DECISIONS.md § How should errors be
 * represented.
 *
 * What it buys: failure is visible in the signature, so a caller cannot forget to handle
 * it; no exception control flow.
 *
 * What it costs:
 *  1. Cancellation must be handled by hand at every boundary. `kotlin.Result` has no
 *     notion of it, and `runCatching` swallows `CancellationException` — which is why
 *     [domainRunCatching] below exists and why `runCatching` must not be used in a
 *     coroutine. See references/kotlin/coroutines-and-flow.md § 8.
 *  2. `kotlin.Result` is a generic value class and is not usable from Swift. A shared
 *     module exporting this shape needs a non-generic facade type at the boundary.
 *     See references/kmp/ios-interop.md § Generics.
 *  3. Unwrapping at each layer, or a `map`/`flatMap` chain, in place of a direct call.
 */
public sealed interface UserOutcome {
    public data class Success(val user: User) : UserOutcome
    public data class Failure(val error: UserError) : UserOutcome
}

public interface ResultStyleUserRepository {
    /** Never throws except for [CancellationException]. */
    public suspend fun fetchUser(): UserOutcome
}

/**
 * The replacement for `runCatching` inside a coroutine.
 *
 * `runCatching` catches `Throwable`, which includes `CancellationException`, so a cancelled
 * job is reported to its parent as a completed one. Every `Result`-style codebase in a
 * coroutine context needs a helper like this; using the stdlib one is a defect.
 */
public inline fun <T> domainRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
