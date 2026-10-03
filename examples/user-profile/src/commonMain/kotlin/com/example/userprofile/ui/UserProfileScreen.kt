package com.example.userprofile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.userprofile.domain.UserProfile
import com.example.userprofile.presentation.UserUiState
import com.example.userprofile.presentation.UserViewModel
import com.example.userprofile.resources.Res
import com.example.userprofile.resources.profile_empty
import com.example.userprofile.resources.profile_loading
import com.example.userprofile.resources.profile_refresh
import com.example.userprofile.resources.profile_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Route: resolves the ViewModel and reads state. Contains no logic, so it is not previewable
 * and not unit-tested — and that is fine.
 *
 * `koinViewModel()` rather than `viewModel()`: on non-JVM targets a parameterless
 * `viewModel()` fails for lack of type reflection; the DI accessor supplies the factory.
 * `collectAsStateWithLifecycle()` rather than `collectAsState()`: the latter keeps collecting
 * while the UI is stopped. See references/kmp/compose-multiplatform.md.
 */
@Composable
public fun UserProfileRoute(
    viewModel: UserViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UserProfileScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onMessageShown = viewModel::onMessageShown,
    )
}

/**
 * Content: values and lambdas only. Previewable, screenshot-testable, and drivable from a
 * Compose test with no DI graph and no ViewModelStoreOwner — which is why the UI test in
 * commonTest can run on JVM and iOS.
 */
@Composable
public fun UserProfileScreen(
    uiState: UserUiState,
    onRefresh: () -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)   // edge-to-edge is mandatory at API 36
                .fillMaxSize(),
        ) {
            when {
                uiState.isInitialLoad -> LoadingState(Modifier.align(Alignment.Center))
                uiState.profile == null -> EmptyState(onRefresh = onRefresh, modifier = Modifier.align(Alignment.Center))
                else -> ProfileContent(
                    profile = uiState.profile,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                )
            }
        }
    }

    // Consume and acknowledge. Keyed on the message id — keyed on Unit it would never re-run
    // for a second message; keyed on the object it re-runs whenever the object is recreated.
    val message = uiState.messages.firstOrNull()
    if (message != null) {
        val text = message.text.resolve()
        LaunchedEffect(message.id) {
            snackbarHostState.showSnackbar(text)
            onMessageShown(message.id)
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.profile_loading)
    CircularProgressIndicator(
        modifier = modifier
            .testTag("loading")
            .semantics { contentDescription = label },   // announce loading, don't just draw it
    )
}

@Composable
private fun EmptyState(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag("empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.profile_empty))
        RefreshButton(onRefresh)
    }
}

@Composable
private fun ProfileContent(
    profile: UserProfile,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("content").padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.profile_title),
            modifier = Modifier.semantics { heading() },  // screen readers can jump here
        )
        Text(profile.label, modifier = Modifier.testTag("label"))
        Text(profile.email, modifier = Modifier.testTag("email"))
        if (isRefreshing) {
            CircularProgressIndicator(Modifier.testTag("refreshing"))
        } else {
            RefreshButton(onRefresh)
        }
    }
}

@Composable
private fun RefreshButton(onRefresh: () -> Unit) {
    Button(onClick = onRefresh, modifier = Modifier.testTag("refresh")) {
        Text(stringResource(Res.string.profile_refresh))
    }
}
