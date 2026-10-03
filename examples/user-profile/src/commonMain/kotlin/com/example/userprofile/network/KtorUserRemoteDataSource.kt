package com.example.userprofile.network

import com.example.userprofile.data.UserDto
import com.example.userprofile.data.UserRemoteDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * The real implementation of the interface `data/UserDataSources.kt` declares.
 *
 * Relative path: the base URL is in `defaultRequest`. Returns the DTO; the repository
 * maps and translates errors. Nothing here catches exceptions — with `expectSuccess`
 * the client throws typed Ktor exceptions and `DefaultUserRepository` is the single
 * translation point.
 */
internal class KtorUserRemoteDataSource(
    private val client: HttpClient,
) : UserRemoteDataSource {
    override suspend fun fetchUser(): UserDto = client.get("me").body()
}
