package org.ggomes.myleo.decision.dual.data

import org.ggomes.myleo.decision.dual.domain.Decider

/**
 * Everything the ViewModel needs to drive one runtime: how to build its engine when the package
 * is on disk, how to download it when it is not, and the hint to show in that case. Grouping
 * these per runtime keeps the swap behind the ViewModel's seams (MVVM) — the UI only picks a
 * [DecisionRuntime].
 */
data class RuntimeBinding(
    val runtime: DecisionRuntime,
    val hint: String,
    /** Null when this runtime's package is not on disk. Building a [Decider] loads no weights. */
    val createDecider: () -> Decider?,
    val downloader: DecisionModelDownloader,
)
