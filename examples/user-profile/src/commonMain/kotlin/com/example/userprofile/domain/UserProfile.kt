package com.example.userprofile.domain

/**
 * What the presentation layer needs: a user resolved against the viewer's display
 * preferences. Produced by [ObserveUserProfileUseCase], not stored anywhere.
 */
public data class UserProfile(
    val id: UserId,
    val label: String,
    val email: String,
    val avatarUrl: String?,
)
