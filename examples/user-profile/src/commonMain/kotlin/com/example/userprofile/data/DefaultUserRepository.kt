package com.example.userprofile.data

import com.example.userprofile.domain.User
import com.example.userprofile.domain.UserError
import com.example.userprofile.domain.UserException
import com.example.userprofile.domain.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first: reads come from the local store, so the UI renders cached data
 * immediately and keeps working without a network. `refresh` writes through.
 *
 * Named for the strategy rather than `UserRepositoryImpl`, so a second strategy
 * (`InMemoryUserRepository`, `FakeUserRepository`) reads as a peer.
 * See references/android/app-architecture.md § Naming conventions.
 *
 * `internal`: callers depend on the domain interface, and the DI module is the only
 * public entry point. See references/architecture/modularization.md § Visibility.
 */
internal class DefaultUserRepository(
    private val remote: UserRemoteDataSource,
    private val local: UserLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : UserRepository {

    override fun observeUser(): Flow<User?> =
        local.observeUser().map { entity -> entity?.toDomain() }

    /**
     * The dispatcher is injected, not referenced as `Dispatchers.IO`, so a test can supply
     * a `TestDispatcher` and stop racing a real thread pool.
     * See references/kotlin/coroutines-and-flow.md § 1.
     *
     * `withContext` is inside the function, which makes it main-safe: callers never have to
     * know where it runs. See § 2 of the same document.
     */
    override suspend fun refresh(): Unit = withContext(ioDispatcher) {
        val dto = try {
            remote.fetchUser()
        } catch (e: CancellationException) {
            // Rethrown before any other handling. Mapping cancellation to a domain error
            // tells the parent the job completed normally and breaks structured concurrency.
            throw e
        } catch (e: Exception) {
            throw UserException(e.toUserError())
        }
        local.put(dto.toEntity())
    }
}

/**
 * The single translation point from transport failures to [UserError].
 *
 * A real implementation matches on the HTTP status — `ClientRequestException`,
 * `ServerResponseException`, `HttpRequestTimeoutException`, `IOException`. This example
 * has no HTTP stack, so it demonstrates the shape only.
 * See references/libraries/ktor-networking.md § Error handling.
 */
private fun Exception.toUserError(): UserError = when (this) {
    is IllegalStateException -> UserError.Offline
    is NoSuchElementException -> UserError.NotFound
    is IllegalArgumentException -> UserError.Unauthorized
    else -> UserError.Unexpected(this)
}
