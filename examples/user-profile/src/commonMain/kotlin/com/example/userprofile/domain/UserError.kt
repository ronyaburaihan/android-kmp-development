package com.example.userprofile.domain

/**
 * The closed set of failures the domain recognises for user data.
 *
 * Closed, because the UI must be able to exhaustively decide what to show. An open
 * hierarchy or a bare `Throwable` pushes that decision to an `else` branch, which is
 * where "Something went wrong" messages come from.
 */
public sealed interface UserError {
    public data object NotFound : UserError
    public data object Unauthorized : UserError
    public data object Offline : UserError
    public data class Unexpected(val cause: Throwable) : UserError
}

/**
 * Carrier for [UserError] across a suspend boundary.
 *
 * Exceptions rather than a `Result`-style wrapper, for three reasons specific to this stack:
 *
 *  1. Cancellation stays correct by construction. A `Result`-returning function has to
 *     decide what to do with [kotlinx.coroutines.CancellationException], and the usual
 *     choice — wrapping it in a failure — breaks structured concurrency.
 *     See references/kotlin/coroutines-and-flow.md § 8.
 *  2. It exports cleanly. `Result<T>` and other generic wrappers are not usable from Swift;
 *     a thrown exception maps onto Swift's `throws` when the function carries `@Throws`.
 *     See references/kmp/ios-interop.md § Generics.
 *  3. The happy path reads without unwrapping at every layer.
 *
 * `alternatives/ResultStyle.kt` carries the `Result`-returning variant, compiled, for
 * projects that already standardised on it. Match the codebase — see examples/DECISIONS.md.
 */
public class UserException(
    public val error: UserError,
) : Exception(error.toString(), (error as? UserError.Unexpected)?.cause)
