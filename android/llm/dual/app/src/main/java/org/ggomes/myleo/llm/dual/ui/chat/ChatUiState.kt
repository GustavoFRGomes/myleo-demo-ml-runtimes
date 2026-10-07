package org.ggomes.myleo.llm.dual.ui.chat

import org.ggomes.myleo.llm.dual.data.RuntimeOption
import org.ggomes.myleo.llm.dual.domain.ChatMessage
import org.ggomes.myleo.llm.dual.domain.ChatModelInfo
import org.ggomes.myleo.llm.dual.domain.ChatRole
import org.ggomes.myleo.llm.dual.domain.GenerationMetrics
import org.ggomes.myleo.llm.dual.domain.GenerationParams

/** Model lifecycle state. */
sealed interface ModelStatus {
    data object Idle : ModelStatus
    data object Loading : ModelStatus
    data class Ready(val info: ChatModelInfo) : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/** Download lifecycle for the model-missing state. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val received: Long, val total: Long) : DownloadState {
        val fraction: Float
            get() = if (total > 0) (received.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
    }
    data object Finished : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** Unidirectional UI state for the chat screen (MVVM). */
data class ChatUiState(
    val runtime: RuntimeOption = RuntimeOption.LITERT_LM,
    val status: ModelStatus = ModelStatus.Idle,
    val download: DownloadState = DownloadState.Idle,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val systemPrompt: String = "You are a helpful assistant.",
    val params: GenerationParams = GenerationParams.Default,
    val isGenerating: Boolean = false,
    val lastMetrics: GenerationMetrics? = null,
    val error: String? = null,
) {
    val canSend: Boolean get() = input.isNotBlank() && !isGenerating && status is ModelStatus.Ready
    val canDownload: Boolean get() = status is ModelStatus.Failed && download !is DownloadState.Downloading
    val canRegenerate: Boolean
        get() = !isGenerating && status is ModelStatus.Ready && messages.any { it.role == ChatRole.USER }
}
