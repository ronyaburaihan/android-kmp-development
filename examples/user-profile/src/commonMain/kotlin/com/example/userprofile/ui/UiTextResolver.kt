package com.example.userprofile.ui

import androidx.compose.runtime.Composable
import com.example.userprofile.presentation.MessageKey
import com.example.userprofile.presentation.UiText
import com.example.userprofile.resources.Res
import com.example.userprofile.resources.msg_offline
import com.example.userprofile.resources.msg_profile_refreshed
import com.example.userprofile.resources.msg_session_expired
import com.example.userprofile.resources.msg_user_load_failed
import com.example.userprofile.resources.msg_user_not_found
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where a message key becomes words. The ViewModel/presenter emits `UiText.Key(MessageKey)`;
 * the UI resolves it against `composeResources/values/strings.xml`.
 *
 * The `when` is exhaustive over a closed enum, so adding a `MessageKey` is a compile error
 * here rather than a missing string at runtime. That is the payoff for making `MessageKey`
 * an enum instead of passing resource ids through shared code — an `R.string` id cannot
 * exist in commonMain, and a CMP `StringResource` must not leak into the presentation layer.
 */
@Composable
internal fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Key -> stringResource(id.toStringResource())
}

private fun MessageKey.toStringResource(): StringResource = when (this) {
    MessageKey.USER_LOAD_FAILED -> Res.string.msg_user_load_failed
    MessageKey.USER_NOT_FOUND -> Res.string.msg_user_not_found
    MessageKey.SESSION_EXPIRED -> Res.string.msg_session_expired
    MessageKey.OFFLINE -> Res.string.msg_offline
    MessageKey.PROFILE_REFRESHED -> Res.string.msg_profile_refreshed
}
