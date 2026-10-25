package org.ggomes.myleo.decision.onnx.ui.decision

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.ggomes.myleo.decision.onnx.FakeDecider
import org.ggomes.myleo.decision.onnx.FakeDownloader
import org.ggomes.myleo.decision.onnx.data.JuliaModelFiles
import org.ggomes.myleo.decision.onnx.domain.DecisionPresets
import org.ggomes.myleo.decision.onnx.domain.QuestionType
import org.ggomes.myleo.decision.onnx.fakeFiles
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DecisionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        locate: () -> JuliaModelFiles? = { fakeFiles() },
        decider: FakeDecider = FakeDecider(),
        downloader: FakeDownloader = FakeDownloader(),
        makeDecider: ((JuliaModelFiles) -> org.ggomes.myleo.decision.onnx.domain.Decider)? = null,
    ) = DecisionViewModel(
        locate = locate,
        makeDecider = makeDecider ?: { decider },
        downloader = downloader,
        ioDispatcher = dispatcher,
    )

    // MARK: - Load / download

    @Test
    fun init_missingFiles_setsFailedAndOffersDownload() = runTest {
        val viewModel = viewModel(locate = { null })
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.status).isInstanceOf(ModelStatus.Failed::class.java)
        assertThat(viewModel.uiState.value.canDownload).isTrue()
    }

    @Test
    fun init_presentFiles_loadsAndSetsReady() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertThat(status).isInstanceOf(ModelStatus.Ready::class.java)
        assertThat((status as ModelStatus.Ready).info.backend).isEqualTo("GPU FP32")
        assertThat(viewModel.uiState.value.download).isEqualTo(DownloadState.Finished)
    }

    @Test
    fun load_failure_setsFailedWithTheRuntimeMessage() = runTest {
        val decider = FakeDecider().apply { loadError = IllegalStateException("weights missing") }

        val viewModel = viewModel(decider = decider)
        advanceUntilIdle()

        val status = viewModel.uiState.value.status
        assertThat(status).isInstanceOf(ModelStatus.Failed::class.java)
        assertThat((status as ModelStatus.Failed).message).contains("weights missing")
        assertThat(viewModel.uiState.value.hasFiles).isTrue()
        assertThat(viewModel.uiState.value.canDownload).isFalse()
        assertThat(viewModel.uiState.value.canRetry).isTrue()
        assertThat(decider.closed).isTrue()
    }

    @Test
    fun retry_afterLoadFailure_buildsAFreshDecider() = runTest {
        val first = FakeDecider().apply { loadError = IllegalStateException("boom") }
        val second = FakeDecider()
        val queue = ArrayDeque<org.ggomes.myleo.decision.onnx.domain.Decider>()
        queue.addLast(first)
        queue.addLast(second)

        val viewModel = viewModel(makeDecider = { queue.removeFirst() })
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canRetry).isTrue()

        viewModel.load()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.status).isInstanceOf(ModelStatus.Ready::class.java)
    }

    @Test
    fun download_success_loadsTheModel() = runTest {
        var files: JuliaModelFiles? = null
        val downloader = FakeDownloader(onComplete = { files = fakeFiles() })
        val viewModel = viewModel(locate = { files }, downloader = downloader)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canDownload).isTrue()

        viewModel.download()
        advanceUntilIdle()

        assertThat(downloader.calls).isEqualTo(1)
        assertThat(viewModel.uiState.value.download).isEqualTo(DownloadState.Finished)
        assertThat(viewModel.uiState.value.status).isInstanceOf(ModelStatus.Ready::class.java)
    }

    @Test
    fun download_failure_setsFailedStateAndKeepsDownloadAvailable() = runTest {
        val downloader = FakeDownloader(error = IllegalStateException("HTTP 503"))
        val viewModel = viewModel(locate = { null }, downloader = downloader)
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
        val viewModel = viewModel(downloader = FakeDownloader(gate = gate))
        advanceUntilIdle()

        viewModel.download()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isDownloading).isTrue()

        viewModel.cancelDownload()

        assertThat(viewModel.uiState.value.download).isEqualTo(DownloadState.Idle)
    }

    // MARK: - Decide

    @Test
    fun decide_blankState_doesNothing() = runTest {
        val decider = FakeDecider()
        val viewModel = viewModel(decider = decider)
        advanceUntilIdle()

        viewModel.decide()
        advanceUntilIdle()

        assertThat(decider.requests).isEmpty()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun decide_blankOption_reportsValidationErrorWithoutCallingTheEngine() = runTest {
        val decider = FakeDecider()
        val viewModel = viewModel(decider = decider)
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
        val viewModel = viewModel(decider = decider)
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
        assertThat(viewModel.uiState.value.results.map { it.type })
            .containsExactly(QuestionType.CHOICE, QuestionType.SCORE, QuestionType.NOUL)
        assertThat(viewModel.uiState.value.isDeciding).isFalse()
    }

    @Test
    fun decide_engineFailure_setsErrorAndClearsBusy() = runTest {
        val decider =
            FakeDecider().apply { decideError = IllegalStateException("graph run failed") }
        val viewModel = viewModel(decider = decider)
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
        assertThat(viewModel.uiState.value.questions).hasSize(3)
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

    @Test
    fun onAddAndRemoveQuestion_updatesTheList() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAddQuestion()
        viewModel.onAddQuestion()
        assertThat(viewModel.uiState.value.questions).hasSize(3)

        viewModel.onRemoveQuestion(1)
        assertThat(viewModel.uiState.value.questions).hasSize(2)
    }
}
