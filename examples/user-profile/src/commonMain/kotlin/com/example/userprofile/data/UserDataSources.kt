package com.example.userprofile.data

import kotlinx.coroutines.flow.Flow

/**
 * One data source per backing store, each returning its own layer's type.
 *
 * `UserRemoteDataSource` is an interface here so the example compiles without a network
 * stack and so tests can substitute a fake. The Ktor implementation — single injected
 * `HttpClient`, `ContentNegotiation`, error translation — is in
 * references/libraries/ktor-networking.md.
 */
internal interface UserRemoteDataSource {
    /** @throws Exception transport failures, translated by the repository. */
    suspend fun fetchUser(): UserDto
}

internal interface UserLocalDataSource {
    fun observeUser(): Flow<UserEntity?>
    suspend fun put(user: UserEntity)
}
