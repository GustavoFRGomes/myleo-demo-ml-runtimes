package org.ggomes.myleolite.ui.ingest

import org.ggomes.myleolite.data.CorpusStats
import org.ggomes.myleolite.data.DocumentStat

/** One corpus chunk with its computed embedding summary. */
data class IngestedChunk(
    val text: String,
    val dimensions: Int,
    val embedMs: Long,
)

/** Timing (+ battery) breakdown of one ingest run, for the performance report. */
data class IngestRunStats(
    val document: String,
    val chunks: Int,
    val embedMs: Long,
    val storeMs: Long,
    val totalMs: Long,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
)

/** Unidirectional UI state for the ingest screen (MVVM). */
data class IngestUiState(
    val corpusText: String = "",
    val isIngesting: Boolean = false,
    val chunks: List<IngestedChunk> = emptyList(),
    val stored: Boolean = false,
    val lastRun: IngestRunStats? = null,
    val corpus: CorpusStats = CorpusStats(),
    val documents: List<DocumentStat> = emptyList(),
    val modelName: String? = null,
    val modelLoadMs: Long? = null,
    val error: String? = null,
)
