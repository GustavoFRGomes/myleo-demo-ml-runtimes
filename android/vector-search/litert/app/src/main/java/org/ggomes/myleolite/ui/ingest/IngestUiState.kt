package org.ggomes.myleolite.ui.ingest

/** One corpus chunk with its computed embedding summary. */
data class IngestedChunk(
    val text: String,
    val dimensions: Int,
    val embedMs: Long,
)

/** Unidirectional UI state for the ingest screen (MVVM). */
data class IngestUiState(
    val corpusText: String = "",
    val isIngesting: Boolean = false,
    val chunks: List<IngestedChunk> = emptyList(),
    val error: String? = null,
)
