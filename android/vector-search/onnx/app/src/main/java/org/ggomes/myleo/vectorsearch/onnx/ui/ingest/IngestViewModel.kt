package org.ggomes.myleo.vectorsearch.onnx.ui.ingest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ggomes.myleo.vectorsearch.onnx.domain.CalculateEmbeddingUseCase
import org.ggomes.myleo.vectorsearch.onnx.domain.chunkCorpus
import kotlin.system.measureTimeMillis

/**
 * Ingest screen ViewModel: chunks the corpus text (F-VS-1) and calculates one
 * embedding per chunk (F-VS-2) off the main thread. Persistence into ObjectBox
 * (F-VS-3) plugs in here next.
 */
class IngestViewModel(
    private val calculateEmbedding: CalculateEmbeddingUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IngestUiState())
    val uiState: StateFlow<IngestUiState> = _uiState.asStateFlow()

    fun onCorpusChange(text: String) {
        _uiState.update { it.copy(corpusText = text, error = null) }
    }

    fun onIngest() {
        val chunks = chunkCorpus(_uiState.value.corpusText)
        if (chunks.isEmpty() || _uiState.value.isIngesting) return
        _uiState.update { it.copy(isIngesting = true, error = null, chunks = emptyList()) }
        viewModelScope.launch(ioDispatcher) {
            val done = mutableListOf<IngestedChunk>()
            var firstError: String? = null
            for (chunk in chunks) {
                var vector: List<Float>? = null
                val ms = measureTimeMillis {
                    calculateEmbedding(chunk)
                        .onSuccess { vector = it }
                        .onFailure { firstError = firstError ?: it.message }
                }
                vector?.let { done += IngestedChunk(chunk, it.size, ms) }
            }
            _uiState.update {
                it.copy(isIngesting = false, chunks = done, error = firstError)
            }
        }
    }
}
