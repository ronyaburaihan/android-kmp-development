package com.example.userprofile.data

/**
 * Storage shape, kept separate from [UserDto] and from the domain `User`.
 *
 * One class serving all three roles has three independent change drivers: a server field
 * rename forces a schema migration, a local-only flag leaks into the JSON contract, and
 * `@Transient` / `@Ignore` accumulate until nobody can tell which fields are real.
 * See references/architecture/clean-architecture.md § Entities and value types.
 *
 * In a real module this would carry Room's `@Entity`/`@PrimaryKey`. It is a plain class
 * here so the example compiles without KSP; the Room form is in
 * references/libraries/room-datastore.md.
 */
internal data class UserEntity(
    val id: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
)
