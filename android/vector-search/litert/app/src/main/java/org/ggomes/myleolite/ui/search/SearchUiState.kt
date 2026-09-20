package org.ggomes.myleolite.ui.search

/** One ranked row in the results list. [similarity] is cosine similarity in 0..1. */
data class SearchHit(
    val text: String,
    val similarity: Double,
)

/** Unidirectional UI state for the search screen (MVVM). */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val queryMs: Long? = null,
    val error: String? = null,
)
