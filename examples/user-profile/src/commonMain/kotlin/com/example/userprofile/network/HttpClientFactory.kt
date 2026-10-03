package com.example.userprofile.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * One configured client for the whole app; the **engine** is injected.
 *
 * Injecting the engine rather than the client keeps this configuration under test: a
 * `MockEngine` in commonTest exercises exactly the ContentNegotiation, Auth and
 * defaultRequest settings production uses. Constructing `HttpClient()` inside a data
 * source would allocate an unclosed connection pool per call and share no auth state.
 * See references/libraries/ktor-networking.md § Client construction.
 */
public fun createHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    tokens: TokenManager,
    json: Json = defaultJson,
): HttpClient = HttpClient(engine) {
    // Non-2xx responses throw; the data source translates them into domain errors.
    expectSuccess = true

    install(ContentNegotiation) { json(json) }

    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }

    install(Auth) {
        bearer {
            // Called when the client needs credentials; cached until invalidated.
            loadTokens { tokens.current()?.toBearer() }
            // Called once after a 401; concurrent 401s share one refresh.
            refreshTokens { tokens.refresh()?.toBearer() }
            // Attach the token on the first request to our own host instead of
            // waiting for a 401 round-trip.
            sendWithoutRequest { request -> request.url.host == baseUrlHost(baseUrl) }
        }
    }

    defaultRequest {
        url(baseUrl)
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }
    // Logging is deliberately absent. `LogLevel.ALL`/`BODY` write bearer tokens and
    // response bodies to logs; install it only in a debug build with `HEADERS` at most.
}

/** `ignoreUnknownKeys` is mandatory: a server adding a field must not break the client. */
public val defaultJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private fun AuthTokens.toBearer(): BearerTokens = BearerTokens(access, refresh)

private fun baseUrlHost(baseUrl: String): String =
    baseUrl.substringAfter("://").substringBefore('/').substringBefore(':')
