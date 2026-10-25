package org.ggomes.myleo.decision.dual.ui.decision

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.ggomes.myleo.decision.dual.FakeDecider
import org.ggomes.myleo.decision.dual.FakeDownloader
import org.ggomes.myleo.decision.dual.binding
import org.ggomes.myleo.decision.dual.data.DecisionRuntime
import org.ggomes.myleo.decision.dual.domain.Accelerator
import org.ggomes.myleo.decision.dual.domain.Decider
import org.ggomes.myleo.decision.dual.domain.DecisionPresets
import org.ggomes.myleo.decision.dual.domain.QuestionType
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DecisionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val onnxInfo = FakeDecider.info(Accelerator.CPU, defaultWindow = 1024, availableWindows = listOf(1024))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        litert: () -> Decider? = { FakeDecider() },
        onnx: () -> Decider? = { FakeDecider(onnxInfo) },
        litertDownloader: FakeDownloader = FakeDownloader(),
        onnxDownloader: FakeDownloader = FakeDownloader(),
    ) = DecisionViewModel(
        bindings =
            mapOf(
                DecisionRuntime.LITERT to binding(DecisionRuntime.LITERT, litert, litertDownloader),
                DecisionRuntime.ONNX to binding(DecisionRuntime.ONNX, onnx, onnxDownloader),
            ),
        ioDispatcher = dispatcher,
    )

    // MARK: - Load

    @Test
    fun init_missingPackages_showsDownloadForTheStartupRuntime() = runTest {
        val viewModel = viewModel(litert = { null }, onnx = { null })
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.runtime).isEqualTo(DecisionRuntime.LITERT)
        assertThat(state.status).isInstanceOf(ModelStatus.Failed::class.java)
        assertThat((state.status as ModelStatus.Failed).message).contains("LiteRT")
        assertThat(state.canDownload).isTrue()
        assertThat(state.hasFiles).isFalse()
    }

    @Test
    fun init_presentPackage_loadsAndSetsReady() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertThat(status).isInstanceOf(ModelStatus.Ready::class.java)
        assertThat((status as ModelStatus.Ready).info.backend).isEqualTo("GPU FP32")
        assertThat(viewModel.uiState.value.download).isEqualTo(DownloadState.Finished)
    }

    @Test
    fun load_failure_offersRetryAndClosesTheFailedEngine() = runTest {
        val decider = FakeDecider().apply { loadError = IllegalStateException("weights missing") }

        val viewModel = viewModel(litert = { decider })
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.status).isInstanceOf(ModelStatus.Failed::class.java)
        assertThat((state.status as ModelStatus.Failed).message).contains("weights missing")
        assertThat(state.canRetry).isTrue()
        assertThat(state.canDownload).isFalse()
        assertThat(decider.closed).isTrue()
    }

    @Test
    fun retry_afterLoadFailure_buildsAFreshDecider() = runTest {
        val first = FakeDecider().apply { loadError = IllegalStateException("boom") }
        val second = FakeDecider()
        val queue = ArrayDeque<Decider>()
        queue.addLast(first)
        queue.addLast(second)

        val viewModel = viewModel(litert = { queue.removeFirst() })
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canRetry).isTrue()

        viewModel.load()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.status).isInstanceOf(ModelStatus.Ready::class.java)
    }

    // MARK: - Download

    @Test
    fun download_success_loadsTheSelectedRuntime() = runTest {
        var files: Decider? = null
        val downloader = FakeDownloader(onComplete = { files = FakeDecider() })

        val viewModel = viewModel(litert = { files }, litertDownloader = downloader)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canDownload).isTrue()

        viewModel.download()
        advanceUntilIdle()

        assertThat(downloader.calls).isEqualTo(1)
        assertThat(viewModel.uiState.value.download).isEqualTo(DownloadState.Finished)
        assertThat(viewModel.uiState.value.status).isInstanceOf(ModelStatus.Ready::class.java)
    }

    @Test
    fun download_usesTheSelectedRuntimesDownloader() = runTest {
        val litertDownloader = FakeDownloader()
        val onnxDownloader = FakeDownloader()
        val viewModel = viewModel(litertDownloader = litertDownloader, onnxDownloader = onnxDownloader)
        advanceUntilIdle()

        viewModel.selectRuntime(DecisionRuntime.ONNX)
        advanceUntilIdle()
        viewModel.download()
        advanceUntilIdle()

        assertThat(litertDownloader.calls).isZero()
        assertThat(onnxDownloader.calls).isEqualTo(1)
    }

    @Test
    fun download_failure_setsFailedStateAndKeepsDownloadAvailable() = runTest {
        val downloader = FakeDownloader(error = IllegalStateException("HTTP 503"))
        val viewModel = viewModel(litert = { null }, litertDownloader = downloader)
        advanceUntilIdle()

        viewModel.download()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.download).isInstanceOf(DownloadState.Failed::class.java)
        assertThat((state.download as DownloadState.Failed).message).contains("HTTP 503")
        assertThat(state.canDownload).isTrue()
    }

    @Test
    fun cancelDownload_stopsAnInFlightDownload() = runTest {
        val gate = CompletableDeferred<Unit>()
        val viewModel = viewModel(litertDownloader = FakeDownloader(gate = gate))
        advanceUntilIdle()

        viewModel.download()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isDownloading).isTrue()

        viewModel.cancelDownload()

        assertThat(viewModel.uiState.value.download).isEqualTo(DownloadState.Idle)
    }

    // MARK: - Runtime selection

    @Test
    fun selectRuntime_swapsTheEngineLoadsTheOtherRuntime_andClearsResults() = runTest {
        val litertDecider = FakeDecider()
        val onnxDecider = FakeDecider(onnxInfo)
        val viewModel = viewModel(litert = { litertDecider }, onnx = { onnxDecider })
        advanceUntilIdle()

        viewModel.applyPreset(DecisionPresets.All.first())
        viewModel.decide()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.results).hasSize(3)

        viewModel.selectRuntime(DecisionRuntime.ONNX)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.runtime).isEqualTo(DecisionRuntime.ONNX)
        assertThat(state.status).isInstanceOf(ModelStatus.Ready::class.java)
        assertThat((state.status as ModelStatus.Ready).info.backend).isEqualTo("CPU")
        assertThat(state.results).isEmpty()
        assertThat(litertDecider.closed).isTrue()
        assertThat(onnxDecider.requests).isEmpty()
    }

    @Test
    fun selectRuntime_missingPackage_showsThatRuntimesHintAndDownload() = runTest {
        val litertDecider = FakeDecider()
        val viewModel = viewModel(litert = { litertDecider }, onnx = { null })
        advanceUntilIdle()

        viewModel.selectRuntime(DecisionRuntime.ONNX)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.runtime).isEqualTo(DecisionRuntime.ONNX)
        assertThat(state.status).isInstanceOf(ModelStatus.Failed::class.java)
        assertThat((state.status as ModelStatus.Failed).message).contains("ONNX")
        assertThat(state.canDownload).isTrue()
        assertThat(litertDecider.closed).isTrue()
    }

    @Test
    fun selectRuntime_preservesTheEditedForm() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStateChange("A customer was charged twice.")
        viewModel.onAddQuestion()

        viewModel.selectRuntime(DecisionRuntime.ONNX)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.stateText).isEqualTo("A customer was charged twice.")
        assertThat(viewModel.uiState.value.questions).hasSize(2)
    }

    @Test
    fun selectRuntime_sameRuntime_isANoOp() = runTest {
        var creations = 0
        val viewModel = viewModel(litert = { FakeDecider().also { creations++ } })
        advanceUntilIdle()
        assertThat(creations).isEqualTo(1)

        viewModel.selectRuntime(DecisionRuntime.LITERT)
        advanceUntilIdle()

        assertThat(creations).isEqualTo(1)
        assertThat(viewModel.uiState.value.status).isInstanceOf(ModelStatus.Ready::class.java)
    }

    @Test
    fun selectRuntime_whileDeciding_isIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        val litertDecider = FakeDecider().apply { decideGate = gate }
        val viewModel = viewModel(litert = { litertDecider })
        advanceUntilIdle()

        viewModel.applyPreset(DecisionPresets.All.first())
        viewModel.decide()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isDeciding).isTrue()

        viewModel.selectRuntime(DecisionRuntime.ONNX)

        assertThat(viewModel.uiState.value.runtime).isEqualTo(DecisionRuntime.LITERT)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    // MARK: - Decide

    @Test
    fun decide_blankState_doesNothing() = runTest {
        val decider = FakeDecider()
        val viewModel = viewModel(litert = { decider })
        advanceUntilIdle()

        viewModel.decide()
        advanceUntilIdle()

        assertThat(decider.requests).isEmpty()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun decide_blankOption_reportsValidationErrorWithoutCallingTheEngine() = runTest {
        val decider = FakeDecider()
        val viewModel = viewModel(litert = { decider })
        advanceUntilIdle()

        viewModel.onStateChange("A customer was charged twice.")
        viewModel.decide()
        advanceUntilIdle()

        assertThat(decider.requests).isEmpty()
        assertThat(viewModel.uiState.value.error).contains("option")
        assertThat(viewModel.uiState.value.isDeciding).isFalse()
    }

    @Test
    fun decide_preset_runsOnePassPerQuestionAndPublishesEveryResult() = runTest {
        val decider = FakeDecider()
        val viewModel = viewModel(litert = { decider })
        advanceUntilIdle()

        viewModel.applyPreset(DecisionPresets.All.first())
        viewModel.decide()
        advanceUntilIdle()

        assertThat(decider.requests).hasSize(3)
        assertThat(decider.requests.map { it.question.type })
            .containsExactly(QuestionType.CHOICE, QuestionType.SCORE, QuestionType.NOUL)
        assertThat(decider.requests.first().question.options.map { it.id })
            .containsExactly("billing", "shipping", "access")
        assertThat(viewModel.uiState.value.results).hasSize(3)
        assertThat(viewModel.uiState.value.isDeciding).isFalse()
    }

    @Test
    fun decide_engineFailure_setsErrorAndClearsBusy() = runTest {
        val decider = FakeDecider().apply { decideError = IllegalStateException("graph run failed") }
        val viewModel = viewModel(litert = { decider })
        advanceUntilIdle()

        viewModel.applyPreset(DecisionPresets.All.first())
        viewModel.decide()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).contains("graph run failed")
        assertThat(viewModel.uiState.value.isDeciding).isFalse()
        assertThat(viewModel.uiState.value.results).isEmpty()
    }

    // MARK: - Editing

    @Test
    fun applyPreset_fillsTheStateAndTheQuestionList() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val preset = DecisionPresets.All[1]

        viewModel.applyPreset(preset)

        assertThat(viewModel.uiState.value.stateText).isEqualTo(preset.state)
        assertThat(viewModel.uiState.value.questions.map { it.type })
            .containsExactly(QuestionType.CHOICE, QuestionType.SCORE, QuestionType.NOUL)
    }

    @Test
    fun onTypeChange_resetsTheOptionShapeForTheNewType() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onTypeChange(0, QuestionType.NOUL)
        assertThat(viewModel.uiState.value.questions.single().options.map { it.id })
            .containsExactly("false", "true")

        viewModel.onTypeChange(0, QuestionType.SCORE)
        assertThat(viewModel.uiState.value.questions.single().options.map { it.id })
            .containsExactly("0", "1", "2")
    }

    @Test
    fun onAddOption_capsAtTwenty() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        repeat(30) { viewModel.onAddOption(0) }

        assertThat(viewModel.uiState.value.questions.single().options).hasSize(20)
    }

    @Test
    fun onRemoveOption_neverDropsBelowTwo() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        repeat(10) { viewModel.onRemoveOption(0, 0) }

        assertThat(viewModel.uiState.value.questions.single().options).hasSize(2)
    }

    @Test
    fun onRemoveQuestion_keepsAtLeastOne() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRemoveQuestion(0)

        assertThat(viewModel.uiState.value.questions).hasSize(1)
    }
}
