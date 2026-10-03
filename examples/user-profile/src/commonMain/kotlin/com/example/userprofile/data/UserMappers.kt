package com.example.userprofile.data

import com.example.userprofile.domain.User
import com.example.userprofile.domain.UserId

/**
 * Mappers live in the data layer, which already knows both the outer type and the domain
 * type. Placing them in the domain would force the domain to import the DTO, inverting
 * the dependency rule.
 *
 * `internal` so the DTO and entity types are not re-exported through a public mapper.
 */
internal fun UserDto.toEntity(): UserEntity =
    UserEntity(
        id = userId,
        displayName = displayName,
        email = email,
        avatarUrl = avatarUrl,
    )

internal fun UserEntity.toDomain(): User =
    User(
        id = UserId(id),
        displayName = displayName,
        email = email,
        avatarUrl = avatarUrl,
    )
