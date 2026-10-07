package org.ggomes.myleo.vectorsearch.dual.ui.runtime

import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime

/** One runtime card on the picker. */
data class RuntimeCard(
    val runtime: InferenceRuntime,
    val bundled: Boolean,
    val loaded: Boolean,
    val loadMs: Long?,
)

/** Unidirectional UI state for the runtime picker (MVVM). */
data class RuntimeUiState(
    val cards: List<RuntimeCard> = InferenceRuntime.entries.map { RuntimeCard(it, bundled = false, loaded = false, loadMs = null) },
    val isLoading: Boolean = false,
    val loading: InferenceRuntime? = null,
    val error: String? = null,
)
