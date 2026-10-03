package com.example.userprofile.domain

import kotlinx.coroutines.flow.Flow

/**
 * Owned by the domain layer, implemented in the data layer.
 *
 * This direction is the dependency rule: the domain declares what it needs and knows
 * nothing about Room, Ktor, or DataStore. Declaring this interface in the data layer and
 * importing it from the domain inverts it, and makes the domain uncompilable without the
 * frameworks. See references/architecture/clean-architecture.md § Module shape.
 *
 * `observe` returns a stream because the user can change from several causes — a refresh,
 * a push, a sign-out. `refresh` is a one-shot suspend function.
 * See references/kotlin/coroutines-and-flow.md § 5.
 */
public interface UserRepository {

    /** Emits the cached user, or `null` when none is cached. Never throws. */
    public fun observeUser(): Flow<User?>

    /**
     * Fetches the current user and updates the cache.
     *
     * @throws UserException with a [UserError] describing the failure.
     */
    public suspend fun refresh()
}
