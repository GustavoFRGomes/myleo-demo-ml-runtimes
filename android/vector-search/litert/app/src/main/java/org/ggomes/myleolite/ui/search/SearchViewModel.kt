package org.ggomes.myleolite.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ggomes.myleolite.domain.CalculateEmbeddingUseCase
import kotlin.system.measureTimeMillis

/** Search screen ViewModel: owns query state, runs the embedding use case off the main thread. */
class SearchViewModel(
    private val calculateEmbedding: CalculateEmbeddingUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
    }

    fun onSearch() {
        val query = _uiState.value.query
        if (query.isBlank() || _uiState.value.isSearching) return
        _uiState.update { it.copy(isSearching = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            var vector: List<Float>? = null
            var failure: Throwable? = null
            val ms = measureTimeMillis {
                calculateEmbedding(query).onSuccess { vector = it }.onFailure { failure = it }
            }
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hits = vector?.let { v ->
                        listOf(EmbeddingHit(query, v.size, ms, v.take(PREVIEW_VALUES)))
                    } ?: it.hits,
                    error = failure?.message,
                )
            }
        }
    }

    companion object {
        const val PREVIEW_VALUES = 8
    }
}
