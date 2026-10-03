package com.example.userprofile.presentation

/**
 * A message the UI can render, chosen by the ViewModel without resolving it.
 *
 * The ViewModel must not hold a `Context` or produce a localised string, so it cannot call
 * `getString`. Putting a `UiText` in the state keeps the ViewModel free of Android types
 * and keeps the string table in the UI layer where the platform can resolve it.
 * See references/architecture/mvvm-udf.md § ViewModel responsibilities.
 *
 * `Key` is deliberately an enum rather than an `Int` resource id: an `R.string` reference
 * is Android-only and will not compile in commonMain.
 */
public sealed interface UiText {
    public data class Raw(val value: String) : UiText
    public data class Key(val id: MessageKey) : UiText
}

public enum class MessageKey {
    USER_LOAD_FAILED,
    USER_NOT_FOUND,
    SESSION_EXPIRED,
    OFFLINE,
    PROFILE_REFRESHED,
}
