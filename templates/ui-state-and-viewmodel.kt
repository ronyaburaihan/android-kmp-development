// Template: one screen's state + ViewModel. See references/architecture/mvvm-udf.md and the
// compiled reference examples/user-profile/.../presentation/UserViewModel.kt.
// Replace <Screen>; delete what the screen does not need; never ship a TODO().

package <pkg>.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One immutable snapshot per screen. All `val`, read-only collections → Compose-stable. */
public data class <Screen>UiState(
    val data: <DomainType>? = null,
    val isRefreshing: Boolean = false,
    val messages: List<UserMessage> = emptyList(),
) {
    public val isInitialLoad: Boolean get() = data == null && isRefreshing
}

public class <Screen>ViewModel(
    private val observe<Thing>: Observe<Thing>UseCase,   // or a repository
    private val repository: <Thing>Repository,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    public val uiState: StateFlow<<Screen>UiState> =
        combine(observe<Thing>(), transient) { data, t ->
            <Screen>UiState(data = data, isRefreshing = t.isRefreshing, messages = t.messages)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), <Screen>UiState())

    public fun refresh() {
        viewModelScope.launch {
            transient.update { it.copy(isRefreshing = true) }
            try {
                repository.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: <Domain>Exception) {
                transient.update { it.withMessage(UiText.Key(e.error.toMessageKey())) }
            } finally {
                transient.update { it.copy(isRefreshing = false) }
            }
        }
    }

    public fun onMessageShown(id: Long) {
        transient.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
    }

    private data class Transient(
        val isRefreshing: Boolean = false,
        val messages: List<UserMessage> = emptyList(),
        val nextId: Long = 0,
    ) {
        fun withMessage(text: UiText) = copy(messages = messages + UserMessage(nextId, text), nextId = nextId + 1)
    }
}
