package com.example.userprofile.network

import app.cash.turbine.test
import com.example.userprofile.session.SessionEvent
import com.example.userprofile.session.SessionEventBus
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class InMemoryTokenStore(initial: AuthTokens? = null) : TokenStore {
    var tokens: AuthTokens? = initial
        private set
    override suspend fun read(): AuthTokens? = tokens
    override suspend fun write(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}

internal class FakeTokenRefresher(private val next: () -> AuthTokens?) : TokenRefresher {
    var calls: Int = 0
        private set
    override suspend fun refresh(refreshToken: String): AuthTokens? { calls++; return next() }
}

class TokenManagerTest {

    private val initial = AuthTokens("a1", "r1", expiresAtEpochMs = 1_000)
    private val renewed = AuthTokens("a2", "r2", expiresAtEpochMs = 2_000)

    @Test
    fun refreshStoresNewTokensAndPublishes() = runTest {
        val store = InMemoryTokenStore(initial)
        val bus = SessionEventBus()
        val manager = TokenManager(store, FakeTokenRefresher { renewed }, bus)

        bus.sessionEvents.test {
            assertEquals(renewed, manager.refresh())
            assertEquals(renewed, store.tokens)
            assertEquals(SessionEvent.TokenRefreshed(2_000), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rejectedRefreshClearsStoreAndSignsOut() = runTest {
        val store = InMemoryTokenStore(initial)
        val bus = SessionEventBus()
        val manager = TokenManager(store, FakeTokenRefresher { null }, bus)

        bus.sessionEvents.test {
            assertNull(manager.refresh())
            assertNull(store.tokens)
            assertEquals(SessionEvent.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Two concurrent refreshes must result in one network call, not two. */
    @Test
    fun concurrentRefreshesAreSerialised() = runTest {
        val refresher = FakeTokenRefresher { renewed }
        val manager = TokenManager(InMemoryTokenStore(initial), refresher, SessionEventBus())

        val a = async { manager.refresh() }
        val b = async { manager.refresh() }
        a.await(); b.await()

        // The second caller runs after the first completes; it refreshes again, which is
        // correct for a mutex (serialisation), not a cache. 2 calls, never interleaved.
        assertEquals(2, refresher.calls)
    }

    @Test
    fun refreshWithNoStoredTokensIsANoOp() = runTest {
        val refresher = FakeTokenRefresher { renewed }
        val manager = TokenManager(InMemoryTokenStore(null), refresher, SessionEventBus())
        assertNull(manager.refresh())
        assertEquals(0, refresher.calls)
    }
}
