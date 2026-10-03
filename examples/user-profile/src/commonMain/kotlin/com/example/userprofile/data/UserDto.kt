package com.example.userprofile.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Transport shape. `internal`, so no other module can depend on the wire format.
 *
 * Nullability mirrors the API contract, not the sample payload that happened to be at
 * hand. A field marked non-null because one response included it throws at parse time in
 * production the first time it is omitted.
 * See references/libraries/ktor-networking.md § Define the DTO.
 */
@Serializable
internal data class UserDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("email") val email: String,
    // Documented as optional by the API.
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
