package org.ggomes.myleo.vectorsearch.dual.ui.runtime

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ggomes.myleo.vectorsearch.dual.data.LoadedRuntime
import org.ggomes.myleo.vectorsearch.dual.data.RuntimeManager
import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime

/**
 * Runtime picker ViewModel: no model loads at startup — the selected runtime
 * initializes here, on demand, off the main thread. Reports the loaded
 * runtime (model name + load ms) back via [onLoaded].
 */
class RuntimeViewModel(
    private val runtimeManager: RuntimeManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onLoaded: (InferenceRuntime, LoadedRuntime) -> Unit = { _, _ -> },
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuntimeUiState())
    val uiState: StateFlow<RuntimeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) { refreshCards() }
    }

    fun onSelect(runtime: InferenceRuntime) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, loading = runtime, error = null) }
        viewModelScope.launch(ioDispatcher) {
            val result = runtimeManager.select(runtime)
            result
                .onSuccess { loaded ->
                    withContext(Dispatchers.Main) { onLoaded(runtime, loaded) }
                    refreshCards()
                    _uiState.update { it.copy(isLoading = false, loading = null) }
                    Log.d(TAG, "${runtime.label} selected")
                }
                .onFailure { error ->
                    refreshCards()
                    _uiState.update {
                        it.copy(isLoading = false, loading = null, error = error.message)
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch(ioDispatcher) { refreshCards() }
    }

    private fun refreshCards() {
        val cards = InferenceRuntime.entries.map { runtime ->
            val loaded = runtimeManager.currentLoaded()?.takeIf { runtimeManager.current == runtime }
            RuntimeCard(
                runtime = runtime,
                bundled = try {
                    runtimeManager.hasBundledModel(runtime)
                } catch (e: Exception) {
                    Log.w(TAG, "bundled check failed: ${e.message}")
                    false
                },
                loaded = runtimeManager.isLoaded(runtime),
                loadMs = loaded?.modelLoadMs,
            )
        }
        _uiState.update { it.copy(cards = cards) }
    }

    companion object {
        private const val TAG = "MyLeo-Runtime"
    }
}
