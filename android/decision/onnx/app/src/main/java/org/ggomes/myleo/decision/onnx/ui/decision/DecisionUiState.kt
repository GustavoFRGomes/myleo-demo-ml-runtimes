package org.ggomes.myleo.decision.onnx.ui.decision

import org.ggomes.myleo.decision.onnx.domain.DecisionOption
import org.ggomes.myleo.decision.onnx.domain.DecisionOutcome
import org.ggomes.myleo.decision.onnx.domain.DecisionQuestion
import org.ggomes.myleo.decision.onnx.domain.ModelInfo
import org.ggomes.myleo.decision.onnx.domain.QuestionType

/** Model lifecycle state. */
sealed interface ModelStatus {
    data object Idle : ModelStatus
    data object Loading : ModelStatus
    data class Ready(val info: ModelInfo) : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/** Package download lifecycle for the model-missing state. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val received: Long, val total: Long) : DownloadState {
        val fraction: Float
            get() = if (total > 0) (received.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
    }
    data object Finished : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** One editable option. Only the text is user-editable; ids are positional or fixed per type. */
data class OptionDraft(val id: String, val text: String)

/**
 * Editable question. [normalized] pins ids to the shape the decoder returns: `oN` for choice,
 * the zero-based index for score, and `false`/`true` for noul.
 */
data class QuestionDraft(
    val type: QuestionType,
    val instructions: String,
    val options: List<OptionDraft>,
) {
    fun normalized(): QuestionDraft =
        when (type) {
            // Choice ids are the caller's labels and survive a preset round trip; only blank
            // ids get a positional fallback so the answer still has something to display.
            QuestionType.CHOICE ->
                copy(
                    options =
                        options.mapIndexed { index, option ->
                            option.copy(id = option.id.ifBlank { "o${index + 1}" })
                        },
                )
            QuestionType.SCORE ->
                copy(options = options.mapIndexed { index, option -> OptionDraft(index.toString(), option.text) })
            QuestionType.NOUL ->
                copy(
                    options =
                        listOf(
                            OptionDraft(DecisionQuestion.FALSE_ID, options.getOrNull(0)?.text.orEmpty()),
                            OptionDraft(DecisionQuestion.TRUE_ID, options.getOrNull(1)?.text.orEmpty()),
                        ),
                )
        }

    /** Throws [IllegalArgumentException] when a description is blank or the shape is invalid. */
    fun toQuestion(): DecisionQuestion {
        val normalized = normalized()
        return DecisionQuestion(
            type = normalized.type,
            instructions = normalized.instructions,
            options = normalized.options.map { DecisionOption(it.id, it.text) },
        )
    }

    companion object {
        fun of(question: DecisionQuestion): QuestionDraft =
            QuestionDraft(
                type = question.type,
                instructions = question.instructions,
                options = question.options.map { OptionDraft(it.id, it.text) },
            )

        fun default(type: QuestionType): QuestionDraft =
            when (type) {
                QuestionType.CHOICE ->
                    QuestionDraft(
                        type = type,
                        instructions = "Which option fits best?",
                        options = listOf(OptionDraft("o1", ""), OptionDraft("o2", "")),
                    )
                QuestionType.SCORE ->
                    QuestionDraft(
                        type = type,
                        instructions = "How does this rate?",
                        options = listOf(OptionDraft("0", ""), OptionDraft("1", ""), OptionDraft("2", "")),
                    )
                QuestionType.NOUL ->
                    QuestionDraft(
                        type = type,
                        instructions = "Is this true?",
                        options = listOf(OptionDraft(DecisionQuestion.FALSE_ID, "No"), OptionDraft(DecisionQuestion.TRUE_ID, "Yes")),
                    )
            }
    }
}

/** Unidirectional UI state for the decision screen (MVVM). */
data class DecisionUiState(
    val status: ModelStatus = ModelStatus.Idle,
    val download: DownloadState = DownloadState.Idle,
    val hasFiles: Boolean = false,
    val stateText: String = "",
    val questions: List<QuestionDraft> = listOf(QuestionDraft.default(QuestionType.CHOICE)),
    val results: List<DecisionOutcome> = emptyList(),
    val isDeciding: Boolean = false,
    val error: String? = null,
) {
    val isDownloading: Boolean get() = download is DownloadState.Downloading

    val canDownload: Boolean
        get() = !hasFiles && !isDownloading && status !is ModelStatus.Loading

    /** Files are on disk but the engine failed (e.g. both backends refused the graph). */
    val canRetry: Boolean
        get() = hasFiles && status is ModelStatus.Failed && !isDownloading

    val canDecide: Boolean
        get() = status is ModelStatus.Ready && !isDeciding && stateText.isNotBlank()

    val isReady: Boolean get() = status is ModelStatus.Ready
}
