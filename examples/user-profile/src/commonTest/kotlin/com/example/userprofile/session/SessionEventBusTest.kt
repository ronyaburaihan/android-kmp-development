package com.example.userprofile.session

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionEventBusTest {

    @Test
    fun deliversToEveryActiveSubscriber() = runTest {
        val bus = SessionEventBus()
        bus.sessionEvents.test {
            bus.sessionEvents.test {
                bus.publish(SessionEvent.SignedOut)
                assertEquals(SessionEvent.SignedOut, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(SessionEvent.SignedOut, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** `replay = 0`: a subscriber that arrives after sign-out must not be told to sign out. */
    @Test
    fun lateSubscriberDoesNotReceiveEarlierEvents() = runTest {
        val bus = SessionEventBus()
        bus.publish(SessionEvent.SignedOut)
        bus.sessionEvents.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** `tryEmit` must succeed with no collector — it is called from non-suspending callbacks. */
    @Test
    fun publishNeverFailsWithoutCollectors() {
        val bus = SessionEventBus()
        // No exception, no suspension, no return value to check: the contract is "fire and forget".
        bus.publish(SessionEvent.TokenRefreshed(expiresAtEpochMs = 1L))
        assertTrue(true)
    }
}
