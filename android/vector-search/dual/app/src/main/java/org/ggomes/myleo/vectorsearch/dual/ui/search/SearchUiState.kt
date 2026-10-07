package org.ggomes.myleo.vectorsearch.dual.ui.search

/** One ranked row in the results list. [similarity] is cosine similarity in 0..1. */
data class SearchHit(
    val text: String,
    val similarity: Double,
    /** Full parent item (email raw string) the chunk came from. */
    val source: String = text,
)

/** Unidirectional UI state for the search screen (MVVM). */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val queryMs: Long? = null,
    val error: String? = null,
)
