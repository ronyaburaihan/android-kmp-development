package com.example.userprofile.data

import com.example.userprofile.domain.UserError
import com.example.userprofile.domain.UserException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private class FakeRemote(
    private var result: () -> UserDto,
) : UserRemoteDataSource {
    override suspend fun fetchUser(): UserDto = result()
    fun failWith(e: Exception) { result = { throw e } }
}

private class FakeLocal : UserLocalDataSource {
    private val rows = MutableStateFlow<UserEntity?>(null)
    override fun observeUser(): Flow<UserEntity?> = rows
    override suspend fun put(user: UserEntity) { rows.value = user }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUserRepositoryTest {

    private val dto = UserDto(
        userId = "u1",
        displayName = "Ada Lovelace",
        email = "ada@example.com",
        avatarUrl = null,
    )

    private fun repository(
        remote: UserRemoteDataSource = FakeRemote { dto },
        local: UserLocalDataSource = FakeLocal(),
    ) = DefaultUserRepository(remote, local, UnconfinedTestDispatcher())

    @Test
    fun observeEmitsNullBeforeAnyRefresh() = runTest {
        assertNull(repository().observeUser().first())
    }

    @Test
    fun refreshWritesThroughToLocalAndSurfacesDomainModel() = runTest {
        val local = FakeLocal()
        val repository = repository(local = local)

        repository.refresh()

        val user = repository.observeUser().first()
        assertEquals("u1", user?.id?.value)
        assertEquals("Ada Lovelace", user?.displayName)
    }

    // Error-translation paths are where most networking bugs live, and they are routinely
    // the untested half. See references/libraries/ktor-networking.md § Testing.
    @Test
    fun translatesTransportFailuresToDomainErrors() = runTest {
        val cases = listOf<Pair<Exception, UserError>>(
            IllegalStateException("no network") to UserError.Offline,
            NoSuchElementException("404") to UserError.NotFound,
            IllegalArgumentException("401") to UserError.Unauthorized,
        )

        for ((thrown, expected) in cases) {
            val remote = FakeRemote { dto }.apply { failWith(thrown) }
            try {
                repository(remote = remote).refresh()
                fail("expected UserException for $thrown")
            } catch (e: UserException) {
                assertEquals(expected, e.error, "$thrown should map to $expected")
            }
        }
    }

    @Test
    fun wrapsUnrecognisedFailureAsUnexpectedPreservingCause() = runTest {
        val cause = RuntimeException("boom")
        val remote = FakeRemote { dto }.apply { failWith(cause) }

        try {
            repository(remote = remote).refresh()
            fail("expected UserException")
        } catch (e: UserException) {
            val error = e.error
            assertTrue(error is UserError.Unexpected)
            assertEquals(cause, error.cause)
        }
    }

    /**
     * Pins that cancellation propagates rather than being converted into a domain error.
     *
     * If `refresh` caught `CancellationException` and threw `UserException` instead, the
     * parent would see a completed child and `join()` would return normally — structured
     * concurrency silently broken. This test is the guard for the ordering of the catch
     * clauses in `DefaultUserRepository.refresh`.
     * See references/kotlin/coroutines-and-flow.md § 8.
     */
    @Test
    fun cancellationPropagatesAndIsNotTranslated() = runTest {
        val gate = CompletableDeferred<UserDto>()
        val hangingRemote = object : UserRemoteDataSource {
            override suspend fun fetchUser(): UserDto = gate.await()
        }
        val repository = repository(remote = hangingRemote)

        var observed: Throwable? = null
        val job = launch {
            try {
                repository.refresh()
            } catch (e: Throwable) {
                observed = e
                throw e
            }
        }

        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(
            observed == null || observed is kotlinx.coroutines.CancellationException,
            "expected cancellation to propagate, got $observed",
        )
    }
}
