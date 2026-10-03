package com.example.userprofile.network

import com.example.userprofile.session.SessionEvent
import com.example.userprofile.session.SessionEventBus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public data class AuthTokens(
    val access: String,
    val refresh: String,
    val expiresAtEpochMs: Long,
)

/**
 * Where tokens live is a security decision, not a networking one. This interface is the
 * seam: the data layer implements it over platform secure storage (Keystore-wrapped on
 * Android, Keychain on iOS), never over DataStore Preferences or Room in plaintext.
 * See references/quality/security.md § Secure storage.
 */
public interface TokenStore {
    public suspend fun read(): AuthTokens?
    public suspend fun write(tokens: AuthTokens)
    public suspend fun clear()
}

/** The refresh call itself — a plain interface so the test can fake it. */
public interface TokenRefresher {
    /** Returns null when the refresh token is rejected; the session is then over. */
    public suspend fun refresh(refreshToken: String): AuthTokens?
}

/**
 * Coordinates token access for the HTTP client.
 *
 * The mutex matters: Ktor already serialises concurrent 401-triggered refreshes, but
 * callers outside Ktor (a background sync) can race it. One refresh at a time, and a
 * failed refresh is published once as [SessionEvent.SignedOut] so every consumer reacts.
 */
public class TokenManager(
    private val store: TokenStore,
    private val refresher: TokenRefresher,
    private val sessionEvents: SessionEventBus,
) {
    private val refreshMutex = Mutex()

    public suspend fun current(): AuthTokens? = store.read()

    public suspend fun refresh(): AuthTokens? = refreshMutex.withLock {
        val existing = store.read() ?: return@withLock null
        val fresh = refresher.refresh(existing.refresh)
        if (fresh == null) {
            store.clear()
            sessionEvents.publish(SessionEvent.SignedOut)
            null
        } else {
            store.write(fresh)
            sessionEvents.publish(SessionEvent.TokenRefreshed(fresh.expiresAtEpochMs))
            fresh
        }
    }

    public suspend fun signOut() {
        store.clear()
        sessionEvents.publish(SessionEvent.SignedOut)
    }
}
