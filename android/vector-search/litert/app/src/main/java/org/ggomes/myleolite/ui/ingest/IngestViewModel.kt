package org.ggomes.myleolite.ui.ingest

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
import org.ggomes.myleolite.data.ChunkEmbedding
import org.ggomes.myleolite.data.CorpusStats
import org.ggomes.myleolite.data.DocumentRepository
import org.ggomes.myleolite.domain.CalculateEmbeddingUseCase
import org.ggomes.myleolite.domain.chunkCorpus
import kotlin.system.measureTimeMillis

/**
 * Ingest screen ViewModel: chunks the corpus text (F-VS-1), calculates one
 * embedding per chunk (F-VS-2), persists them into ObjectBox (F-VS-3) and
 * reports timings + index totals for the performance log — all off the main
 * thread. One ingest run = one document ("Document N").
 */
class IngestViewModel(
    private val calculateEmbedding: CalculateEmbeddingUseCase,
    private val documents: DocumentRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val modelLoadMs: Long? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IngestUiState(modelLoadMs = modelLoadMs))
    val uiState: StateFlow<IngestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            documents.stats()
                .onSuccess { stats -> _uiState.update { it.copy(corpus = stats) } }
                .onFailure { Log.w(TAG, "stats failed: ${it.message}") }
        }
    }

    fun onCorpusChange(text: String) {
        _uiState.update { it.copy(corpusText = text, error = null) }
    }

    fun onClearIndex() {
        if (_uiState.value.isIngesting) return
        viewModelScope.launch(ioDispatcher) {
            documents.clear()
                .onSuccess {
                    _uiState.update {
                        it.copy(chunks = emptyList(), stored = false, lastRun = null, corpus = CorpusStats())
                    }
                    Log.d(TAG, "index cleared")
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun onIngest() {
        val rawChunks = chunkCorpus(_uiState.value.corpusText)
        if (rawChunks.isEmpty() || _uiState.value.isIngesting) return
        _uiState.update { it.copy(isIngesting = true, error = null, chunks = emptyList(), stored = false, lastRun = null) }
        viewModelScope.launch(ioDispatcher) {
            val label = "Document ${(documents.stats().getOrNull()?.documents ?: 0) + 1}"
            val embedded = mutableListOf<Triple<String, List<Float>, Long>>()
            var embedTotalMs = 0L
            var storeMs = 0L
            var stored = false
            var firstError: String? = null
            val totalMs = measureTimeMillis {
                rawChunks.forEachIndexed { index, chunk ->
                    var vector: List<Float>? = null
                    val ms = measureTimeMillis {
                        calculateEmbedding(chunk)
                            .onSuccess { vector = it }
                            .onFailure { firstError = firstError ?: it.message }
                    }
                    vector?.let {
                        embedded += Triple(chunk, it, ms)
                        embedTotalMs += ms
                        Log.d(TAG, "embed chunk ${index + 1}/${rawChunks.size} (${chunk.length} chars) in ${ms}ms")
                    }
                }
                if (embedded.isNotEmpty() && firstError == null) {
                    storeMs = measureTimeMillis {
                        documents.ingest(label, embedded.map { ChunkEmbedding(it.first, it.second) })
                            .onSuccess { stored = true }
                            .onFailure { firstError = it.message }
                    }
                }
            }
            val snapshot = embedded.map { (text, v, ms) -> IngestedChunk(text, v.size, ms) }
            val corpus = documents.stats().getOrElse { CorpusStats() }
            _uiState.update {
                it.copy(
                    isIngesting = false,
                    chunks = snapshot,
                    stored = stored,
                    lastRun = IngestRunStats(label, snapshot.size, embedTotalMs, storeMs, totalMs),
                    corpus = corpus,
                    error = firstError,
                )
            }
            Log.d(TAG, "ingested $label: ${snapshot.size} chunks in ${totalMs}ms (embed ${embedTotalMs}ms, store ${storeMs}ms)")
            Log.d(TAG, "index holds ${corpus.documents} documents, ${corpus.chunks} chunks")
        }
    }

    companion object {
        private const val TAG = "MyLeo-Ingest"
    }
}
