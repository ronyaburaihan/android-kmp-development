package com.example.userprofile.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.example.userprofile.domain.UserId
import com.example.userprofile.domain.UserProfile
import com.example.userprofile.presentation.MessageKey
import com.example.userprofile.presentation.UiText
import com.example.userprofile.presentation.UserMessage
import com.example.userprofile.presentation.UserUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compose UI tests in commonTest, run on JVM (desktop) and iOS simulator via the CMP **v2**
 * API (`androidx.compose.ui.test.v2.runComposeUiTest`). Possible only because the content
 * composable takes state and lambdas — no DI graph, no ViewModelStoreOwner.
 *
 * Nodes are addressed by `testTag`, never by rendered text: text is localised.
 */
@OptIn(ExperimentalTestApi::class)
class UserProfileScreenTest {

    private val profile = UserProfile(
        id = UserId("u1"),
        label = "Ada Lovelace",
        email = "ada@example.com",
        avatarUrl = null,
    )

    @Test
    fun showsLoadingOnInitialLoad() = runComposeUiTest {
        setContent { UserProfileScreen(UserUiState(isRefreshing = true), onRefresh = {}, onMessageShown = {}) }
        onNodeWithTag("loading").assertIsDisplayed()
    }

    @Test
    fun showsEmptyStateWhenNoProfile() = runComposeUiTest {
        setContent { UserProfileScreen(UserUiState(), onRefresh = {}, onMessageShown = {}) }
        onNodeWithTag("empty").assertIsDisplayed()
    }

    @Test
    fun showsContentWithProfileFields() = runComposeUiTest {
        setContent { UserProfileScreen(UserUiState(profile = profile), onRefresh = {}, onMessageShown = {}) }
        onNodeWithTag("content").assertIsDisplayed()
        onNodeWithTag("label").assertTextEquals("Ada Lovelace")
        onNodeWithTag("email").assertTextEquals("ada@example.com")
    }

    @Test
    fun showsRefreshingIndicatorWhileRefreshingWithContent() = runComposeUiTest {
        setContent { UserProfileScreen(UserUiState(profile = profile, isRefreshing = true), onRefresh = {}, onMessageShown = {}) }
        onNodeWithTag("refreshing").assertIsDisplayed()
    }

    @Test
    fun refreshInvokesCallback() = runComposeUiTest {
        var refreshed = false
        setContent { UserProfileScreen(UserUiState(profile = profile), onRefresh = { refreshed = true }, onMessageShown = {}) }
        onNodeWithTag("refresh").performClick()
        assertTrue(refreshed)
    }

    /** The acknowledgement half: showing a message must call back with its id. */
    @Test
    fun messageIsAcknowledgedAfterBeingShown() = runComposeUiTest {
        var shown: Long? = null
        val state = UserUiState(
            profile = profile,
            messages = listOf(UserMessage(id = 7, text = UiText.Key(MessageKey.PROFILE_REFRESHED))),
        )
        setContent { UserProfileScreen(state, onRefresh = {}, onMessageShown = { shown = it }) }
        waitUntil(timeoutMillis = 5_000) { shown != null }
        assertEquals(7L, shown)
    }
}
