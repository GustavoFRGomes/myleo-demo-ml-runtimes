package org.ggomes.myleolite.ui.search

/** One row in the results list. Today: the query's own embedding (ranked document hits plug in here once the ObjectBox index lands). */
data class EmbeddingHit(
    val text: String,
    val dimensions: Int,
    val embedMs: Long,
    val preview: List<Float>,
)

/** Unidirectional UI state for the search screen (MVVM). */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hits: List<EmbeddingHit> = emptyList(),
    val error: String? = null,
)
