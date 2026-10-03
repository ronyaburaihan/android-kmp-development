package com.example.userprofile.session

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

public sealed interface SessionEvent {
    public data object SignedOut : SessionEvent
    public data class TokenRefreshed(val expiresAtEpochMs: Long) : SessionEvent
}

/**
 * Where `SharedFlow` is the right tool: a **broadcast to several consumers** in the data
 * layer — repositories that must drop their caches on sign-out, a token store that must
 * clear, an analytics client that must reset its user id.
 *
 * Where it is the wrong tool: ViewModel → UI one-off events. Delivery depends on a
 * collector being active at emit time, so a stopped UI drops or duplicates the event.
 * Those belong in state with an acknowledgement — see presentation/UserViewModel.kt and
 * references/architecture/mvvm-udf.md § One-off events.
 *
 * Configuration:
 *  - `replay = 0`: a late subscriber must not receive a stale sign-out.
 *  - `extraBufferCapacity = 16` + `DROP_OLDEST`: `tryEmit` never suspends and never fails,
 *    so a non-suspending caller (a Ktor auth callback, a platform callback) can emit.
 *    `BufferOverflow.SUSPEND` with capacity 0 would make `tryEmit` return false whenever
 *    no collector is active.
 *
 * Only the read-only projection is public; the single writer is this class.
 */
public class SessionEventBus {

    private val events = MutableSharedFlow<SessionEvent>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    public val sessionEvents: SharedFlow<SessionEvent> = events.asSharedFlow()

    /** Non-suspending by design; safe from callbacks. */
    public fun publish(event: SessionEvent) {
        events.tryEmit(event)
    }
}
