package com.example.userprofile.domain

import kotlinx.coroutines.flow.Flow

public enum class NameFormat { FULL, INITIALS }

public data class DisplayPreferences(
    val nameFormat: NameFormat = NameFormat.FULL,
    val showAvatars: Boolean = true,
)

public interface SettingsRepository {
    public fun observeDisplayPreferences(): Flow<DisplayPreferences>
}
