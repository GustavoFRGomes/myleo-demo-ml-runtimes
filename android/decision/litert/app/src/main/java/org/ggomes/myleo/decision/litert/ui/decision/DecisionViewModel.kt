package org.ggomes.myleo.decision.litert.ui.decision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ggomes.myleo.decision.litert.data.DecisionModelDownloader
import org.ggomes.myleo.decision.litert.data.JuliaModelFiles
import org.ggomes.myleo.decision.litert.data.JuliaModelLocator
import org.ggomes.myleo.decision.litert.data.ReportExporter
import org.ggomes.myleo.decision.litert.data.SystemStatsProvider
import org.ggomes.myleo.decision.litert.domain.Decider
import org.ggomes.myleo.decision.litert.domain.DecisionPreset
import org.ggomes.myleo.decision.litert.domain.DecisionRequest
import org.ggomes.myleo.decision.litert.domain.DecisionSuite
import org.ggomes.myleo.decision.litert.domain.QuestionType
import org.ggomes.myleo.decision.litert.domain.ReportFormat
import org.ggomes.myleo.decision.litert.domain.SuiteReport
import org.ggomes.myleo.decision.litert.domain.SuiteReportFormat
import org.ggomes.myleo.decision.litert.domain.SuiteResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Decision screen ViewModel (MVVM): package download, model load, question editing, one forward
 * pass per question, and the fixed 18-prompt suite with its benchmark report. Seams are injected
 * so unit tests never touch the file system, network, battery or the LiteRT runtime.
 */
class DecisionViewModel(
    private val locate: () -> JuliaModelFiles?,
    private val makeDecider: (JuliaModelFiles) -> Decider,
    private val downloader: DecisionModelDownloader,
    private val stats: SystemStatsProvider,
    private val exporter: ReportExporter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DecisionUiState())
    val uiState: StateFlow<DecisionUiState> = _uiState.asStateFlow()

    private var decider: Decider? = null
    private var loadJob: Job? = null
    private var downloadJob: Job? = null
    private var decideJob: Job? = null
    private var suiteJob: Job? = null

    init {
        load()
    }

    // MARK: - Mode

    fun selectMode(mode: DecisionMode) {
        val state = _uiState.value
        if (mode == state.mode || state.isBusy) return
        _uiState.update { it.copy(mode = mode, error = null, message = null) }
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
                    releaseDecider()
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

    // MARK: - Prompt suite

    fun runSuite() {
        val current = _uiState.value
        if (!current.canRunSuite) return
        val engine = decider ?: return
        val info = (current.status as ModelStatus.Ready).info
        val device = stats.device()
        val batteryStart = stats.battery()
        val startedAt = stats.nowMs()

        _uiState.update {
            it.copy(
                suite = SuiteProgress(running = true),
                report = null,
                error = null,
                message = null,
            )
        }
        suiteJob = viewModelScope.launch(ioDispatcher) {
            val collected = mutableListOf<SuiteResult>()
            try {
                for (case in DecisionSuite.Cases) {
                    currentCoroutineContext().ensureActive()
                    val outcome = engine.decide(DecisionRequest(case.state, case.question))
                    collected += SuiteResult(case, outcome)
                    val snapshot = collected.toList()
                    _uiState.update { state ->
                        state.copy(
                            suite =
                                state.suite.copy(
                                    completed = snapshot.size,
                                    results = snapshot,
                                    promptMs = snapshot.sumOf { it.outcome.timings.totalMs },
                                ),
                        )
                    }
                }

                val completed = collected.toList()
                val report =
                    SuiteReport(
                        runtimeLabel = RUNTIME_LABEL,
                        backend = info.backend,
                        window = info.defaultWindow,
                        device = device,
                        loadMs = info.totalLoadMs,
                        tokenizerLoadMs = info.tokenizerLoadMs,
                        graphLoadMs = info.graphLoadMs,
                        batteryStart = batteryStart,
                        batteryEnd = stats.battery(),
                        startedAtEpochMs = startedAt,
                        finishedAtEpochMs = stats.nowMs(),
                        results = completed,
                    )
                _uiState.update {
                    it.copy(
                        suite =
                            it.suite.copy(
                                running = false,
                                completed = completed.size,
                                results = completed,
                                promptMs = report.promptMs,
                            ),
                        report = report,
                    )
                }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(suite = it.suite.copy(running = false)) }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        suite = it.suite.copy(running = false),
                        error = error.message ?: "suite failed",
                    )
                }
            }
        }
    }

    fun cancelSuite() {
        suiteJob?.cancel()
        _uiState.update { it.copy(suite = it.suite.copy(running = false)) }
    }

    /** Writes the report to Downloads and surfaces the location as a message. */
    fun exportReport(format: ReportFormat) {
        val report = _uiState.value.report ?: return
        val stamp =
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(report.finishedAtEpochMs))
        val fileName = "${report.fileNameStem}-$stamp.${format.extension}"
        val content =
            when (format) {
                ReportFormat.MARKDOWN -> SuiteReportFormat.markdown(report)
                ReportFormat.JSON -> SuiteReportFormat.json(report)
            }
        exporter.export(fileName, format.mimeType, content)
            .onSuccess { location -> _uiState.update { it.copy(message = "Saved $location") } }
            .onFailure { error ->
                _uiState.update { it.copy(message = error.message ?: "export failed") }
            }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

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
        suiteJob?.cancel()
        runCatching { decider?.close() }
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

    /** Drops the current engine off the main thread; the next [load] builds a fresh one. */
    private fun releaseDecider() {
        val previous = decider ?: return
        decider = null
        viewModelScope.launch(ioDispatcher) { runCatching { previous.close() } }
    }

    private companion object {
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 20
        const val RUNTIME_LABEL = "LiteRT"
    }
}
