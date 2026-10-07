package org.ggomes.myleo.llm.onnxgenai.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ggomes.myleo.llm.onnxgenai.data.OnnxGenAIModelLocator
import org.ggomes.myleo.llm.onnxgenai.data.ModelDownloader
import org.ggomes.myleo.llm.onnxgenai.domain.ChatInference
import org.ggomes.myleo.llm.onnxgenai.domain.ChatMessage
import org.ggomes.myleo.llm.onnxgenai.domain.ChatRole
import org.ggomes.myleo.llm.onnxgenai.domain.GenerationMetrics
import org.ggomes.myleo.llm.onnxgenai.domain.GenerationParams

/**
 * Chat screen ViewModel (MVVM): model download + load, streaming generation,
 * cancel/regenerate/clear, and TTFT + tokens/sec reporting. Seams are injected
 * so unit tests never touch the file system, network, or the runtime.
 */
class ChatViewModel(
    private val locate: () -> File?,
    private val makeInference: (File) -> ChatInference,
    private val downloader: ModelDownloader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var inference: ChatInference? = null
    private var generationJob: Job? = null
    private var downloadJob: Job? = null

    // MARK: - Model lifecycle

    fun loadModel() {
        if (_uiState.value.status is ModelStatus.Loading) return
        val file = locate()
        if (file == null) {
            _uiState.update { it.copy(status = ModelStatus.Failed(OnnxGenAIModelLocator.HINT)) }
            return
        }
        val engine = inference ?: makeInference(file).also { inference = it }
        _uiState.update { it.copy(status = ModelStatus.Loading, error = null) }
        viewModelScope.launch(ioDispatcher) {
            runCatching { engine.loadModel() }
                .onSuccess { info ->
                    _uiState.update { it.copy(status = ModelStatus.Ready(info), download = DownloadState.Finished) }
                }
                .onFailure { e ->
                    Log.e(TAG, "model load failed", e)
                    _uiState.update { it.copy(status = ModelStatus.Failed(e.message ?: "failed to load model")) }
                }
        }
    }

    fun downloadModel() {
        if (_uiState.value.download is DownloadState.Downloading) return
        _uiState.update { it.copy(download = DownloadState.Downloading(0, 0), error = null) }
        downloadJob = viewModelScope.launch(ioDispatcher) {
            runCatching {
                downloader.download { received, total ->
                    _uiState.update { it.copy(download = DownloadState.Downloading(received, total)) }
                }
            }
                .onSuccess {
                    _uiState.update { it.copy(download = DownloadState.Finished, status = ModelStatus.Idle) }
                    loadModel()
                }
                .onFailure { e ->
                    if (e is CancellationException) {
                        _uiState.update { it.copy(download = DownloadState.Idle) }
                    } else {
                        _uiState.update { it.copy(download = DownloadState.Failed(e.message ?: "download failed")) }
                    }
                }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _uiState.update { it.copy(download = DownloadState.Idle) }
    }

    // MARK: - Input

    fun onInputChange(text: String) = _uiState.update { it.copy(input = text) }
    fun onSystemPromptChange(text: String) = _uiState.update { it.copy(systemPrompt = text) }
    fun onParamsChange(params: GenerationParams) = _uiState.update { it.copy(params = params.clamped()) }
    fun dismissError() = _uiState.update { it.copy(error = null) }

    // MARK: - Generation

    fun send() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.isGenerating || _uiState.value.status !is ModelStatus.Ready) return
        _uiState.update {
            it.copy(
                messages = it.messages +
                    ChatMessage(role = ChatRole.USER, text = text) +
                    ChatMessage(role = ChatRole.ASSISTANT, text = "", isStreaming = true),
                input = "",
            )
        }
        startGeneration()
    }

    fun regenerate() {
        if (_uiState.value.isGenerating || _uiState.value.status !is ModelStatus.Ready) return
        _uiState.update { state ->
            val trimmed = state.messages.dropLastWhile { it.role == ChatRole.ASSISTANT }
            if (trimmed.lastOrNull()?.role != ChatRole.USER) {
                state
            } else {
                state.copy(
                    messages = trimmed + ChatMessage(role = ChatRole.ASSISTANT, text = "", isStreaming = true),
                )
            }
        }
        startGeneration()
    }

    fun clear() {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(messages = emptyList(), lastMetrics = null, error = null) }
    }

    fun cancel() {
        generationJob?.cancel()
        viewModelScope.launch(ioDispatcher) { runCatching { inference?.cancel() } }
        _uiState.update { state ->
            state.copy(
                isGenerating = false,
                messages = state.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
            )
        }
    }

    private fun startGeneration() {
        val engine = inference ?: return
        val history = _uiState.value.messages.filter { !it.isStreaming }
        val systemPrompt = _uiState.value.systemPrompt
        val params = _uiState.value.params
        _uiState.update { it.copy(isGenerating = true, lastMetrics = null, error = null) }

        generationJob = viewModelScope.launch(ioDispatcher) {
            val startedAt = System.currentTimeMillis()
            val tokensBefore = runCatching { engine.tokenCount() }.getOrDefault(0)
            var firstChunkAt: Long? = null
            try {
                engine.generate(systemPrompt, history, params).collect { chunk ->
                    if (firstChunkAt == null && chunk.isNotEmpty()) firstChunkAt = System.currentTimeMillis()
                    appendChunk(chunk)
                }
                finishGeneration(
                    startedAt = startedAt,
                    firstChunkAt = firstChunkAt,
                    generatedTokens = (runCatching { engine.tokenCount() }.getOrDefault(tokensBefore) - tokensBefore)
                        .coerceAtLeast(0),
                )
            } catch (e: CancellationException) {
                finishGeneration(startedAt = startedAt, firstChunkAt = firstChunkAt, generatedTokens = 0)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "generation failed", e)
                failGeneration(e)
            }
        }
    }

    private fun appendChunk(chunk: String) {
        _uiState.update { state ->
            val index = state.messages.indexOfLast { it.isStreaming }
            if (index < 0) {
                state
            } else {
                val messages = state.messages.toMutableList()
                messages[index] = messages[index].copy(text = messages[index].text + chunk)
                state.copy(messages = messages)
            }
        }
    }

    private fun finishGeneration(startedAt: Long, firstChunkAt: Long?, generatedTokens: Int) {
        val endedAt = System.currentTimeMillis()
        val totalMs = endedAt - startedAt
        val ttftMs = firstChunkAt?.let { it - startedAt } ?: totalMs
        val metrics = GenerationMetrics(
            ttftMs = ttftMs,
            totalMs = totalMs,
            generatedTokens = generatedTokens,
        )
        _uiState.update { state ->
            val index = state.messages.indexOfLast { it.role == ChatRole.ASSISTANT }
            val messages = state.messages.toMutableList()
            if (index >= 0) messages[index] = messages[index].copy(isStreaming = false, metrics = metrics)
            state.copy(messages = messages, isGenerating = false, lastMetrics = metrics)
        }
    }

    private fun failGeneration(e: Exception) {
        _uiState.update { state ->
            state.copy(
                isGenerating = false,
                error = e.message ?: "generation failed",
                messages = state.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
            )
        }
    }

    private companion object {
        const val TAG = "MyLeo-ONNX"
    }
}
