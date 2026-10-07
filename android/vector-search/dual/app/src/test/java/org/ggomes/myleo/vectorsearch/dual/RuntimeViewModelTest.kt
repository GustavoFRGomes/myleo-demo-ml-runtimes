package org.ggomes.myleo.vectorsearch.dual

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ggomes.myleo.vectorsearch.dual.data.ChunkEmbedding
import org.ggomes.myleo.vectorsearch.dual.data.CorpusStats
import org.ggomes.myleo.vectorsearch.dual.data.DocumentRepository
import org.ggomes.myleo.vectorsearch.dual.data.DocumentStat
import org.ggomes.myleo.vectorsearch.dual.data.LoadedRuntime
import org.ggomes.myleo.vectorsearch.dual.data.RuntimeManager
import org.ggomes.myleo.vectorsearch.dual.data.ScoredChunk
import org.ggomes.myleo.vectorsearch.dual.domain.Embedder
import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime
import org.ggomes.myleo.vectorsearch.dual.ui.runtime.RuntimeViewModel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onSelect_success_reportsLoadedAndClearsLoading() = runTest {
        var loaded: Pair<InferenceRuntime, LoadedRuntime>? = null
        val viewModel = RuntimeViewModel(
            RuntimeManager(FakeDocumentRepository(), load = { FakeLoaded(it) }),
            testDispatcher,
        ) { runtime, result -> loaded = runtime to result }

        viewModel.onSelect(InferenceRuntime.LITERT)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(loaded?.first).isEqualTo(InferenceRuntime.LITERT)
        assertThat(state.cards.first { it.runtime == InferenceRuntime.LITERT }.loaded).isTrue()
    }

    @Test
    fun onSelect_failure_showsErrorAndReportsNothing() = runTest {
        var called = false
        val viewModel = RuntimeViewModel(
            RuntimeManager(FakeDocumentRepository(), load = { throw IllegalStateException("no model") }),
            testDispatcher,
        ) { _, _ -> called = true }

        viewModel.onSelect(InferenceRuntime.ONNX)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo("no model")
        assertThat(called).isFalse()
    }

    @Test
    fun onSelect_whileLoading_ignoresSecondTap() = runTest {
        var loads = 0
        val viewModel = RuntimeViewModel(
            RuntimeManager(FakeDocumentRepository(), load = { loads++; FakeLoaded(it) }),
            testDispatcher,
        )
        advanceUntilIdle()

        viewModel.onSelect(InferenceRuntime.LITERT)
        viewModel.onSelect(InferenceRuntime.ONNX)
        advanceUntilIdle()

        assertThat(loads).isEqualTo(1)
    }

    private fun FakeLoaded(runtime: InferenceRuntime) = LoadedRuntime(
        embedder = object : Embedder {
            override fun embed(text: String): List<Float> = listOf(0.6f, 0.8f)
        },
        modelName = runtime.modelFile,
        modelLoadMs = 7,
    )

    private class FakeDocumentRepository : DocumentRepository {
        override fun ingest(document: String, chunks: List<ChunkEmbedding>): Result<Unit> =
            Result.success(Unit)

        override fun clear(): Result<Unit> = Result.success(Unit)

        override fun stats(): Result<CorpusStats> = Result.success(CorpusStats())

        override fun documentStats(): Result<List<DocumentStat>> = Result.success(emptyList())

        override fun search(vector: List<Float>, topK: Int): Result<List<ScoredChunk>> =
            Result.success(emptyList())
    }
}
