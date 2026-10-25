package org.ggomes.myleo.decision.onnx.ui.decision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ggomes.myleo.decision.onnx.data.DecisionModelDownloader
import org.ggomes.myleo.decision.onnx.data.JuliaModelFiles
import org.ggomes.myleo.decision.onnx.data.JuliaModelLocator
import org.ggomes.myleo.decision.onnx.domain.Decider
import org.ggomes.myleo.decision.onnx.domain.DecisionPreset
import org.ggomes.myleo.decision.onnx.domain.DecisionRequest
import org.ggomes.myleo.decision.onnx.domain.QuestionType

/**
 * Decision screen ViewModel (MVVM): package download, model load, question editing and one
 * forward pass per question. Seams are injected so unit tests never touch the file system,
 * network or the ONNX runtime.
 */
class DecisionViewModel(
    private val locate: () -> JuliaModelFiles?,
    private val makeDecider: (JuliaModelFiles) -> Decider,
    private val downloader: DecisionModelDownloader,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DecisionUiState())
    val uiState: StateFlow<DecisionUiState> = _uiState.asStateFlow()

    private var decider: Decider? = null
    private var loadJob: Job? = null
    private var downloadJob: Job? = null
    private var decideJob: Job? = null

    init {
        load()
    }

    // MARK: - Model lifecycle

    fun load() {
        if (_uiState.value.status is ModelStatus.Loading) return
        val files = locate()
        _uiState.update { it.copy(hasFiles = files != null) }
        if (files == null) {
            _uiState.update { it.copy(status = ModelStatus.Failed(JuliaModelLocator.HINT)) }
            return
        }
        val engine = decider ?: makeDecider(files).also { decider = it }
        _uiState.update { it.copy(status = ModelStatus.Loading, error = null) }
        loadJob = viewModelScope.launch(ioDispatcher) {
            runCatching { engine.load() }
                .onSuccess { info ->
                    _uiState.update {
                        it.copy(status = ModelStatus.Ready(info), download = DownloadState.Finished)
                    }
                }
                .onFailure { error ->
                    // Drop the failed engine so Retry starts from a clean tokenizer/table/graph.
                    decider?.close()
                    decider = null
                    _uiState.update {
                        it.copy(status = ModelStatus.Failed(error.message ?: "could not load Julia-1"))
                    }
                }
        }
    }

    fun download() {
        if (_uiState.value.isDownloading) return
        _uiState.update { it.copy(download = DownloadState.Downloading(0, 0), error = null) }
        downloadJob = viewModelScope.launch(ioDispatcher) {
            runCatching {
                downloader.download { received, total ->
                    _uiState.update { it.copy(download = DownloadState.Downloading(received, total)) }
                }
            }
                .onSuccess {
                    _uiState.update { it.copy(download = DownloadState.Finished, status = ModelStatus.Idle) }
                    load()
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        _uiState.update { it.copy(download = DownloadState.Idle) }
                    } else {
                        _uiState.update {
                            it.copy(download = DownloadState.Failed(error.message ?: "download failed"))
                        }
                    }
                }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _uiState.update { it.copy(download = DownloadState.Idle) }
    }

    // MARK: - Input

    fun onStateChange(text: String) = _uiState.update { it.copy(stateText = text) }

    fun applyPreset(preset: DecisionPreset) =
        _uiState.update {
            it.copy(
                stateText = preset.state,
                questions = preset.questions.map(QuestionDraft::of),
                results = emptyList(),
                error = null,
            )
        }

    fun onTypeChange(index: Int, type: QuestionType) = updateQuestion(index) { current ->
        if (current.type == type) {
            current
        } else {
            current.copy(type = type, options = QuestionDraft.default(type).options)
        }
    }

    fun onInstructionsChange(index: Int, text: String) = updateQuestion(index) {
        it.copy(instructions = text)
    }

    fun onOptionChange(questionIndex: Int, optionIndex: Int, text: String) = updateQuestion(questionIndex) { current ->
        current.copy(
            options = current.options.mapIndexed { index, option ->
                if (index == optionIndex) option.copy(text = text) else option
            },
        )
    }

    fun onAddOption(questionIndex: Int) = updateQuestion(questionIndex) { current ->
        if (current.type == QuestionType.NOUL || current.options.size >= MAX_OPTIONS) {
            current
        } else {
            current.copy(options = current.options + OptionDraft("", ""))
        }
    }

    fun onRemoveOption(questionIndex: Int, optionIndex: Int) = updateQuestion(questionIndex) { current ->
        if (current.type == QuestionType.NOUL || current.options.size <= MIN_OPTIONS) {
            current
        } else {
            current.copy(options = current.options.filterIndexed { index, _ -> index != optionIndex })
        }
    }

    fun onAddQuestion() =
        _uiState.update {
            it.copy(questions = it.questions + QuestionDraft.default(QuestionType.CHOICE), results = emptyList())
        }

    fun onRemoveQuestion(index: Int) = _uiState.update { state ->
        if (state.questions.size <= 1) {
            state
        } else {
            state.copy(questions = state.questions.filterIndexed { i, _ -> i != index }, results = emptyList())
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    // MARK: - Decide

    fun decide() {
        val current = _uiState.value
        if (!current.canDecide) return
        val engine = decider ?: return

        val questions =
            runCatching { current.questions.map { it.toQuestion() } }
                .getOrElse { error ->
                    _uiState.update { it.copy(error = error.message ?: "invalid question") }
                    return
                }

        val stateText = current.stateText.trim()
        _uiState.update { it.copy(isDeciding = true, results = emptyList(), error = null) }
        decideJob = viewModelScope.launch(ioDispatcher) {
            runCatching { questions.map { engine.decide(DecisionRequest(stateText, it)) } }
                .onSuccess { results -> _uiState.update { it.copy(isDeciding = false, results = results) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isDeciding = false, error = error.message ?: "decision failed")
                    }
                }
        }
    }

    override fun onCleared() {
        decider?.close()
        decider = null
        super.onCleared()
    }

    private fun updateQuestion(index: Int, transform: (QuestionDraft) -> QuestionDraft) =
        _uiState.update { state ->
            if (index !in state.questions.indices) {
                state
            } else {
                state.copy(
                    questions = state.questions.mapIndexed { i, draft -> if (i == index) transform(draft) else draft },
                    results = emptyList(),
                )
            }
        }

    private companion object {
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 20
    }
}
