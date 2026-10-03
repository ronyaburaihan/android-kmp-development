package com.example.userprofile.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Mappers and parsing are pure functions and are where field-mismatch bugs live. Cheap to
 * test, high yield. See references/quality/testing-strategy.md § 4.1.
 *
 * The payload is a verbatim capture, not a hand-written approximation — a hand-written one
 * encodes the author's assumption about the contract rather than the contract.
 */
class UserMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val payload = """
        {
          "user_id": "u1",
          "display_name": "Ada Lovelace",
          "email": "ada@example.com",
          "avatar_url": "https://example.com/a.png"
        }
    """.trimIndent()

    @Test
    fun parsesCapturedPayload() {
        val dto = json.decodeFromString<UserDto>(payload)

        assertEquals("u1", dto.userId)
        assertEquals("Ada Lovelace", dto.displayName)
        assertEquals("https://example.com/a.png", dto.avatarUrl)
    }

    /** The field is documented as optional, so its absence must not throw. */
    @Test
    fun parsesPayloadWithAvatarAbsent() {
        val withoutAvatar = """
            {"user_id":"u1","display_name":"Ada","email":"ada@example.com"}
        """.trimIndent()

        assertNull(json.decodeFromString<UserDto>(withoutAvatar).avatarUrl)
    }

    /**
     * Guards `ignoreUnknownKeys = true`. Without it, the server adding a field breaks every
     * installed client — a release-blocking outage caused by an additive server change.
     * See references/libraries/ktor-networking.md § DTOs and serialization.
     */
    @Test
    fun toleratesUnknownField() {
        val withExtra = """
            {"user_id":"u1","display_name":"Ada","email":"ada@example.com","tier":"gold"}
        """.trimIndent()

        assertEquals("u1", json.decodeFromString<UserDto>(withExtra).userId)
    }

    @Test
    fun mapsDtoThroughEntityToDomain() {
        val domain = json.decodeFromString<UserDto>(payload).toEntity().toDomain()

        assertEquals("u1", domain.id.value)
        assertEquals("Ada Lovelace", domain.displayName)
        assertEquals("ada@example.com", domain.email)
        assertEquals("https://example.com/a.png", domain.avatarUrl)
    }
}
