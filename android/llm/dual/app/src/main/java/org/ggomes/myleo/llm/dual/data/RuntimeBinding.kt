package org.ggomes.myleo.llm.dual.data

import java.io.File
import org.ggomes.myleo.llm.dual.domain.ChatInference

/**
 * Everything the ViewModel needs to drive one runtime: where its model lives, how to
 * build its engine, and how to download it when missing. Grouping these per option
 * keeps the runtime swap behind the ViewModel's seams (MVVM) — the UI only picks a
 * [RuntimeOption].
 */
data class RuntimeBinding(
    val option: RuntimeOption,
    val hint: String,
    val locate: () -> File?,
    val makeInference: (File) -> ChatInference,
    val downloader: ModelDownloader,
)
