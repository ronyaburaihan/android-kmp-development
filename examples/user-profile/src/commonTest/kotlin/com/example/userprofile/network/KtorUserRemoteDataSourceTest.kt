package com.example.userprofile.network

import com.example.userprofile.session.SessionEventBus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `MockEngine` exercises the real client configuration — ContentNegotiation, Auth,
 * defaultRequest — against canned responses. No network, runs on every target.
 * See references/libraries/ktor-networking.md § Testing.
 */
class KtorUserRemoteDataSourceTest {

    private val payload = """{"user_id":"u1","display_name":"Ada Lovelace","email":"ada@example.com"}"""
    private val tokens = AuthTokens("access-1", "refresh-1", expiresAtEpochMs = 9_999)

    private fun source(handler: MockRequestHandler): KtorUserRemoteDataSource {
        val engine = MockEngine(handler)
        val manager = TokenManager(InMemoryTokenStore(tokens), FakeTokenRefresher { null }, SessionEventBus())
        val client = createHttpClient(engine, baseUrl = "https://api.example.com/", tokens = manager)
        return KtorUserRemoteDataSource(client)
    }

    @Test
    fun hitsRelativePathUnderBaseUrlWithBearerToken() = runTest {
        var seenPath: String? = null
        var seenAuth: String? = null
        val source = source { request ->
            seenPath = request.url.encodedPath
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond(payload, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val dto = source.fetchUser()

        assertEquals("/me", seenPath)
        assertEquals("Bearer access-1", seenAuth)      // sendWithoutRequest attached it up front
        assertEquals("u1", dto.userId)
        assertEquals("Ada Lovelace", dto.displayName)
    }

    @Test
    fun unknownFieldsAreTolerated() = runTest {
        val source = source {
            respond(
                """{"user_id":"u1","display_name":"Ada","email":"a@b.c","tier":"gold"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertEquals("u1", source.fetchUser().userId)
    }

    /** With `expectSuccess = true` a 4xx throws a typed exception the repository translates. */
    @Test
    fun clientErrorThrowsTypedException() = runTest {
        val source = source { respondError(HttpStatusCode.NotFound) }
        assertFailsWith<ClientRequestException> { source.fetchUser() }
    }
}
