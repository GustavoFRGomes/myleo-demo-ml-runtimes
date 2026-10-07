package org.ggomes.myleo.vectorsearch.onnx.ui.ingest

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
import org.ggomes.myleo.vectorsearch.onnx.data.ChunkEmbedding
import org.ggomes.myleo.vectorsearch.onnx.data.CorpusStats
import org.ggomes.myleo.vectorsearch.onnx.data.DocumentRepository
import org.ggomes.myleo.vectorsearch.onnx.data.DocumentStat
import org.ggomes.myleo.vectorsearch.onnx.domain.CalculateEmbeddingUseCase
import org.ggomes.myleo.vectorsearch.onnx.domain.EmailDatasetLoader
import org.ggomes.myleo.vectorsearch.onnx.domain.SystemStatsProvider
import org.ggomes.myleo.vectorsearch.onnx.domain.UnknownSystemStats
import org.ggomes.myleo.vectorsearch.onnx.domain.chunkCorpus
import kotlin.system.measureTimeMillis

private data class IngestGroup(
    val isEmail: Boolean,
    val source: String,
    val chunks: List<String>,
)

/**
 * Ingest screen ViewModel: chunks the corpus text (F-VS-1), calculates one
 * embedding per chunk (F-VS-2), persists them into ObjectBox (F-VS-3) and
 * reports timings, battery + index totals for the performance log — all off
 * the main thread. Free text ingests as one "Document N"; each Enron email
 * becomes its own "Email N" keeping the parent email as source for detail.
 */
class IngestViewModel(
    private val calculateEmbedding: CalculateEmbeddingUseCase,
    private val documents: DocumentRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val modelLoadMs: Long? = null,
    private val modelName: String? = null,
    private val systemStats: SystemStatsProvider = UnknownSystemStats,
    private val datasetLoader: EmailDatasetLoader? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IngestUiState(modelName = modelName, modelLoadMs = modelLoadMs))
    val uiState: StateFlow<IngestUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) { refreshIndexState() }
    }

    fun onCorpusChange(text: String) {
        _uiState.update { it.copy(corpusText = text, error = null) }
    }

    fun onSourceChange(source: IngestSource) {
        _uiState.update { it.copy(source = source, error = null) }
    }

    fun onItemCountChange(count: Int) {
        _uiState.update { it.copy(itemCount = maxOf(1, count), error = null) }
    }

    fun onClearIndex() {
        if (_uiState.value.isIngesting) return
        viewModelScope.launch(ioDispatcher) {
            documents.clear()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            chunks = emptyList(),
                            stored = false,
                            lastRun = null,
                            corpus = CorpusStats(),
                            documents = emptyList(),
                        )
                    }
                    Log.d(TAG, "index cleared")
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }

    fun onIngest() {
        if (_uiState.value.isIngesting) return
        when (_uiState.value.source) {
            IngestSource.FREE_TEXT -> ingestFreeText()
            IngestSource.ENRON_SAMPLE -> ingestEnronSample()
        }
    }

    private fun ingestFreeText() {
        val corpus = _uiState.value.corpusText
        val groups = listOf(IngestGroup(isEmail = false, source = corpus, chunks = chunkCorpus(corpus)))
        if (groups.none { it.chunks.isNotEmpty() }) return
        beginRun()
        viewModelScope.launch(ioDispatcher) { runIngest(groups) }
    }

    private fun ingestEnronSample() {
        val loader = datasetLoader
        if (loader == null) {
            _uiState.update { it.copy(error = "no dataset loader wired") }
            return
        }
        val count = _uiState.value.itemCount
        beginRun()
        viewModelScope.launch(ioDispatcher) {
            val emails = try {
                withContext(ioDispatcher) { loader.loadEmails(count) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isIngesting = false, error = e.message) }
                return@launch
            }
            // Each email raw string goes through the same text chunker and
            // becomes its own document, keeping the parent email for detail.
            val groups = emails.map { email ->
                IngestGroup(isEmail = true, source = email, chunks = chunkCorpus(email))
            }.filter { it.chunks.isNotEmpty() }
            runIngest(groups)
        }
    }

    private fun beginRun() {
        _uiState.update { it.copy(isIngesting = true, error = null, chunks = emptyList(), stored = false, lastRun = null) }
    }

    private suspend fun runIngest(groups: List<IngestGroup>) {
        var number = documents.stats().getOrNull()?.documents ?: 0
        val batteryStart = systemStats.snapshot().batteryPercent
        val embedded = mutableListOf<Triple<String, List<Float>, Long>>()
        var embedTotalMs = 0L
        var storeMs = 0L
        var stored = false
        var firstError: String? = null
        val labels = mutableListOf<String>()
        val totalMs = measureTimeMillis {
            for (group in groups) {
                number += 1
                val label = if (group.isEmail) "Email $number" else "Document $number"
                labels += label
                val groupEmbedded = mutableListOf<Triple<String, List<Float>, Long>>()
                for (chunk in group.chunks) {
                    var vector: List<Float>? = null
                    val ms = measureTimeMillis {
                        calculateEmbedding(chunk)
                            .onSuccess { vector = it }
                            .onFailure { firstError = firstError ?: it.message }
                    }
                    vector?.let {
                        groupEmbedded += Triple(chunk, it, ms)
                        embedTotalMs += ms
                        Log.d(TAG, "embed chunk ${embedded.size + groupEmbedded.size}/${groups.sumOf { it.chunks.size }} (${chunk.length} chars) in ${ms}ms")
                    }
                }
                if (groupEmbedded.isNotEmpty() && firstError == null) {
                    storeMs += measureTimeMillis {
                        documents.ingest(
                            label,
                            groupEmbedded.map { ChunkEmbedding(it.first, it.second, group.source) },
                        )
                            .onSuccess { stored = true }
                            .onFailure { firstError = it.message }
                    }
                }
                embedded += groupEmbedded
            }
        }
        val batteryEnd = systemStats.snapshot().batteryPercent
        val snapshot = embedded.map { (text, v, ms) -> IngestedChunk(text, v.size, ms) }
        val corpus = documents.stats().getOrElse { CorpusStats() }
        val perDocument = documents.documentStats().getOrElse { emptyList() }
        val runLabel = when (labels.size) {
            0 -> "no documents"
            1 -> labels[0]
            else -> "Emails ${number - labels.size + 1}-$number"
        }
        _uiState.update {
            it.copy(
                isIngesting = false,
                chunks = snapshot,
                stored = stored,
                lastRun = IngestRunStats(runLabel, snapshot.size, embedTotalMs, storeMs, totalMs, batteryStart, batteryEnd),
                corpus = corpus,
                documents = perDocument,
                error = firstError,
            )
        }
        Log.d(TAG, "ingested $runLabel: ${snapshot.size} chunks in ${totalMs}ms (embed ${embedTotalMs}ms, store ${storeMs}ms), battery ${pct(batteryStart)} → ${pct(batteryEnd)}")
        Log.d(TAG, "index holds ${corpus.documents} documents, ${corpus.chunks} chunks")
    }

    private suspend fun refreshIndexState() {
        val corpus = documents.stats().getOrElse {
            Log.w(TAG, "stats failed: ${it.message}")
            CorpusStats()
        }
        val perDocument = documents.documentStats().getOrElse { emptyList<DocumentStat>() }
        val available = try {
            withContext(ioDispatcher) { datasetLoader?.availableEmails() }
        } catch (e: Exception) {
            Log.w(TAG, "dataset size failed: ${e.message}")
            null
        }
        _uiState.update {
            it.copy(
                corpus = corpus,
                documents = perDocument,
                availableItems = available,
                itemCount = if (available != null) minOf(maxOf(it.itemCount, 1), available) else it.itemCount,
            )
        }
    }

    companion object {
        private const val TAG = "MyLeo-Ingest"
        private fun pct(value: Int?): String = value?.let { "$it%" } ?: "n/a"
    }
}
