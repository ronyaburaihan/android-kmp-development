#!/usr/bin/env bash
# Scaffold the house project structure (see PROJECT_STRUCTURE.md).
#   scaffold.sh init    --root <srcDir> --package <pkg>
#   scaffold.sh screen  <Name>    --root <srcDir> --package <pkg>
#   scaffold.sh feature <feature> --root <srcDir> --package <pkg>
# Generates directories, the navigation key types, and 3-file MVVM/UDF screen sets.
# Only creates files that do not exist; never overwrites.
set -euo pipefail

CMD="${1:-}"; shift || true
NAME=""; ROOT="src/commonMain/kotlin"; PKG=""
case "$CMD" in screen|feature) NAME="${1:-}"; shift || true;; esac
while [ $# -gt 0 ]; do case "$1" in
  --root) ROOT="$2"; shift 2;; --package) PKG="$2"; shift 2;; *) echo "unknown arg $1"; exit 2;; esac; done
[ -n "$PKG" ] || { echo "--package is required"; exit 2; }
BASE="$ROOT/$(echo "$PKG" | tr . /)"

mk() { mkdir -p "$BASE/$1"; }
write() { # write <relpath> ; stdin = content. Skips existing files.
  local f="$BASE/$1"; if [ -e "$f" ]; then echo "  exists  $1"; cat >/dev/null; else mkdir -p "$(dirname "$f")"; cat > "$f"; echo "  created $1"; fi; }

init() {
  for d in \
    core/common/{result,exception,logger,coroutine,dispatcher,time,constants,extensions} \
    core/network/{client,config,interceptor,connectivity,serialization,error} \
    core/security/{encryption,securestorage,device} \
    core/platform/{audio,camera,clipboard,file,permission,share,system,uri} \
    core/analytics/{event,parameter,tracker} core/ads/{banner,interstitial,rewarded,config} \
    core/billing/{product,purchase,subscription,config} core/notification/{permission,channel,scheduler} \
    core/review core/di \
    domain/{model,repository,usecase} domain/service/{conversation,translation,speech,texttospeech,camera,document} \
    domain/event/{conversation,translation,speech,subscription} \
    data/repository data/source/local/room/{database,dao,entity,migration} data/source/local/datastore \
    data/source/remote/auth/dto data/model data/mapper data/analytics/{mapper,tracker} data/di \
    presentation/app presentation/component presentation/screen presentation/navigation \
    presentation/permission presentation/theme presentation/util presentation/di; do mk "$d"; done

  write presentation/navigation/AppRoute.kt <<K
package $PKG.presentation.navigation

import kotlinx.serialization.Serializable

/** Full-screen destinations: data, not screens. Carry ids, never whole models. */
@Serializable
public sealed interface AppRoute {
    @Serializable public data object Main : AppRoute
}
K
  write presentation/navigation/MainTab.kt <<K
package $PKG.presentation.navigation

import kotlinx.serialization.Serializable

/**
 * Destinations of the bottom-navigation host. A separate type from [AppRoute] so a tab can
 * never be sent to the app-level back stack, and the reverse.
 */
@Serializable
public sealed interface MainTab {
    @Serializable public data object Home : MainTab
}
K
  echo "init done: $BASE"
}

screen() {
  local N="$NAME"; [ -n "$N" ] || { echo "screen <Name> required"; exit 2; }
  case "$N" in -*) echo "screen <Name> required"; exit 2;; esac
  local lc; lc="$(echo "$N" | tr '[:upper:]' '[:lower:]')"
  local P="presentation/screen/$lc"
  write "$P/${N}UiState.kt" <<K
package $PKG.presentation.screen.$lc

import androidx.compose.runtime.Immutable

/** Everything ${N}Screen draws. One immutable object; no lambdas, no flows. */
@Immutable
public data class ${N}UiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)
K
  write "$P/${N}ViewModel.kt" <<K
package $PKG.presentation.screen.$lc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State down through [uiState], actions up as plain method calls. No effect channel. */
public class ${N}ViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(${N}UiState())
    public val uiState: StateFlow<${N}UiState> = _uiState.asStateFlow()

    init { refresh() }

    public fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                // TODO: call a use case / repository; put failures in ${N}UiState
            } catch (e: CancellationException) {
                throw e
            } finally {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }
}
K
  write "$P/${N}Screen.kt" <<K
package $PKG.presentation.screen.$lc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Where ${N} can lead. Built by the navigation graph; the screen knows no routes. */
@Immutable
public data class ${N}NavigationActions(
    val onBack: () -> Unit,
)

/** What the user can do on ${N}Screen. Built by [${N}Route]; defaults keep previews short. */
@Immutable
public data class ${N}Actions(
    val onRefresh: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Route: resolves the ViewModel, collects state, merges navigation and ViewModel calls. No layout. */
@Composable
public fun ${N}Route(
    navigationActions: ${N}NavigationActions,
    viewModel: ${N}ViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ${N}Screen(
        uiState = uiState,
        actions = ${N}Actions(
            onRefresh = viewModel::refresh,
            onBack = navigationActions.onBack,
        ),
    )
}

/** Screen: values in, lambdas out. Previewable and testable without DI or navigation. */
@Composable
public fun ${N}Screen(
    uiState: ${N}UiState,
    actions: ${N}Actions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (uiState.isLoading) CircularProgressIndicator()
    }
}
K
  echo "screen $N done"
}

feature() {
  local f="$NAME"; [ -n "$f" ] || { echo "feature <name> required"; exit 2; }
  for d in domain/model/$f domain/repository/$f domain/usecase/$f data/repository/$f data/source/remote/$f/dto \
           data/source/local/room/dao/$f data/source/local/room/entity/$f data/model/$f data/mapper/$f; do mk "$d"; done
  echo "feature $f packages created"
}

case "$CMD" in init) init;; screen) screen;; feature) feature;; *) echo "usage: scaffold.sh init|screen <Name>|feature <name> --root <dir> --package <pkg>"; exit 2;; esac
