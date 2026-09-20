package org.ggomes.myleo.vectorsearch.onnx.ui.search

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
import org.ggomes.myleo.vectorsearch.onnx.data.DocumentRepository
import org.ggomes.myleo.vectorsearch.onnx.domain.CalculateEmbeddingUseCase
import kotlin.system.measureTimeMillis

/**
 * Search screen ViewModel: embeds the query (F-VS-2) then ranks indexed
 * chunks from ObjectBox (F-VS-4) — all off the main thread.
 */
class SearchViewModel(
    private val calculateEmbedding: CalculateEmbeddingUseCase,
    private val documents: DocumentRepository,
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
            var hits: List<SearchHit>? = null
            var failure: Throwable? = null
            var embedMs = 0L
            var searchMs = 0L
            embedMs = measureTimeMillis {
                calculateEmbedding(query)
                    .onSuccess { vector ->
                        searchMs = measureTimeMillis {
                            documents.search(vector)
                                .onSuccess { found ->
                                    hits = found.map { SearchHit(it.text, it.similarity) }
                                }
                                .onFailure { failure = it }
                        }
                    }
                    .onFailure { failure = it }
            }
            val totalMs = embedMs + searchMs
            _uiState.update {
                it.copy(
                    isSearching = false,
                    hits = hits ?: it.hits,
                    queryMs = hits?.let { totalMs },
                    error = failure?.message,
                )
            }
            Log.d(TAG, "search query embedded in ${embedMs}ms, ${hits?.size ?: 0} hits ranked in ${searchMs}ms")
        }
    }

    companion object {
        private const val TAG = "MyLeo-Search"
    }
}
