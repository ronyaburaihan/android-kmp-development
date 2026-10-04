#!/usr/bin/env bash
# Scaffold the house project structure (see PROJECT_STRUCTURE.md).
#   scaffold.sh init    --root <srcDir> --package <pkg>
#   scaffold.sh screen  <Name>    --root <srcDir> --package <pkg>
#   scaffold.sh feature <feature> --root <srcDir> --package <pkg>
# Generates directories, the presentation/state MVI base types, and 5-file screen sets.
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
    presentation/app presentation/component/{common,button,dialog,language,audio,animation,loading,error,toolbar,bottomsheet} \
    presentation/screen presentation/navigation presentation/state presentation/theme; do mk "$d"; done

  write presentation/state/UiState.kt <<K
package $PKG.presentation.state

/** Immutable snapshot of one screen. Implementations are data classes with val properties only. */
public interface UiState
K
  write presentation/state/UiEvent.kt <<K
package $PKG.presentation.state

/** A user or system event handled by a ViewModel. One sealed hierarchy per screen. */
public interface UiEvent
K
  write presentation/state/UiEffect.kt <<K
package $PKG.presentation.state

/**
 * A one-off effect consumed exactly once by the UI (snackbar, haptic, forward navigation).
 * Anything the user could miss and need again belongs in [UiState], not here.
 * See PROJECT_STRUCTURE.md § The Effect trade-off.
 */
public interface UiEffect
K
  write presentation/state/MviViewModel.kt <<K
package $PKG.presentation.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The MVI container. One immutable [uiState], events in through [handle], effects out through
 * [effects] — a buffered channel, so an effect emitted while the UI is stopped is held until a
 * collector resumes and delivered exactly once. Lost only on process death; durable outcomes
 * belong in state.
 */
public abstract class MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(
    initialState: S,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    public val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _effects = Channel<F>(Channel.BUFFERED)
    public val effects: Flow<F> = _effects.receiveAsFlow()

    /** The only entry point from the UI. */
    public fun handle(event: E) { onEvent(event) }

    protected abstract fun onEvent(event: E)

    protected val currentState: S get() = _uiState.value

    /** Atomic state transition. */
    protected fun reduce(transform: S.() -> S) { _uiState.update(transform) }

    protected fun emitEffect(effect: F) { _effects.trySend(effect) }

    /** Launch in [viewModelScope] with the cancellation contract enforced. */
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
    }
}
K
  write presentation/navigation/AppRoute.kt <<K
package $PKG.presentation.navigation

import kotlinx.serialization.Serializable

/** Navigation keys: data, not screens. Carry ids, never whole models. */
public sealed interface AppRoute {
    @Serializable public data object Home : AppRoute
}
K
  write presentation/navigation/NavigationEffect.kt <<K
package $PKG.presentation.navigation

import $PKG.presentation.state.UiEffect

/** Forward-navigation requests screens hand to the app-level owner of the back stack. */
public sealed interface NavigationEffect : UiEffect {
    public data class To(val route: AppRoute) : NavigationEffect
    public data object Back : NavigationEffect
}
K
  echo "init done: $BASE"
}

screen() {
  local N="$NAME"; [ -n "$N" ] || { echo "screen <Name> required"; exit 2; }
  local lc; lc="$(echo "$N" | tr '[:upper:]' '[:lower:]')"
  local P="presentation/screen/$lc"
  write "$P/${N}UiState.kt" <<K
package $PKG.presentation.screen.$lc

import $PKG.presentation.state.UiState

public data class ${N}UiState(
    val isLoading: Boolean = false,
) : UiState
K
  write "$P/${N}UiEvent.kt" <<K
package $PKG.presentation.screen.$lc

import $PKG.presentation.state.UiEvent

public sealed interface ${N}UiEvent : UiEvent {
    public data object Load : ${N}UiEvent
}
K
  write "$P/${N}UiEffect.kt" <<K
package $PKG.presentation.screen.$lc

import $PKG.presentation.state.UiEffect

public sealed interface ${N}UiEffect : UiEffect {
    public data class ShowMessage(val text: String) : ${N}UiEffect
}
K
  write "$P/${N}ViewModel.kt" <<K
package $PKG.presentation.screen.$lc

import $PKG.presentation.state.MviViewModel

public class ${N}ViewModel : MviViewModel<${N}UiState, ${N}UiEvent, ${N}UiEffect>(${N}UiState()) {

    override fun onEvent(event: ${N}UiEvent) {
        when (event) {
            ${N}UiEvent.Load -> load()
        }
    }

    private fun load() {
        launch {
            reduce { copy(isLoading = true) }
            // TODO: call a use case / repository; map failures to state or an effect
            reduce { copy(isLoading = false) }
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** Route: resolves the ViewModel, collects state and effects. No logic. */
@Composable
public fun ${N}Route(
    viewModel: ${N}ViewModel = koinViewModel(),
    onEffect: (${N}UiEffect) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect(onEffect)      // stops with the composition
    }
    LaunchedEffect(viewModel) { viewModel.handle(${N}UiEvent.Load) }
    ${N}Screen(state = state, onEvent = viewModel::handle)
}

/** Content: values and lambdas only. Previewable and testable without DI. */
@Composable
public fun ${N}Screen(
    state: ${N}UiState,
    onEvent: (${N}UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (state.isLoading) CircularProgressIndicator()
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
