package org.ggomes.myleo.vectorsearch.onnx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ggomes.myleo.vectorsearch.onnx.data.ChunkEmbedding
import org.ggomes.myleo.vectorsearch.onnx.data.CorpusStats
import org.ggomes.myleo.vectorsearch.onnx.data.DocumentRepository
import org.ggomes.myleo.vectorsearch.onnx.data.ScoredChunk
import org.ggomes.myleo.vectorsearch.onnx.domain.CalculateEmbeddingUseCase
import org.ggomes.myleo.vectorsearch.onnx.ui.search.SearchViewModel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

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
    fun onSearch_validQuery_publishesRankedHits() = runTest {
        val documents = FakeDocumentRepository().apply {
            hits = listOf(ScoredChunk("doc one", 0.9), ScoredChunk("doc two", 0.7))
        }
        val viewModel = viewModel(documents)

        viewModel.onQueryChange("hi")
        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSearching).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.hits.map { it.text }).containsExactly("doc one", "doc two")
        assertThat(state.hits.map { it.similarity }).containsExactly(0.9, 0.7)
        assertThat(state.queryMs).isNotNull()
        assertThat(documents.lastQuery).containsExactly(0.6f, 0.8f)
    }

    @Test
    fun onSearch_blankQuery_doesNothing() = runTest {
        val viewModel = viewModel()

        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSearching).isFalse()
        assertThat(state.hits).isEmpty()
        assertThat(state.error).isNull()
    }

    @Test
    fun onSearch_embeddingFailure_showsError() = runTest {
        val repository = FakeEmbeddingRepository().apply { error = RuntimeException("no model") }
        val viewModel = SearchViewModel(CalculateEmbeddingUseCase(repository), FakeDocumentRepository(), testDispatcher)

        viewModel.onQueryChange("hi")
        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSearching).isFalse()
        assertThat(state.hits).isEmpty()
        assertThat(state.error).isEqualTo("no model")
    }

    @Test
    fun onSearch_searchFailure_showsError() = runTest {
        val documents = FakeDocumentRepository().apply { error = RuntimeException("index broken") }
        val viewModel = viewModel(documents)

        viewModel.onQueryChange("hi")
        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSearching).isFalse()
        assertThat(state.hits).isEmpty()
        assertThat(state.error).isEqualTo("index broken")
    }

    @Test
    fun onSearch_emptyIndex_showsNoHitsNorError() = runTest {
        val viewModel = viewModel(FakeDocumentRepository())

        viewModel.onQueryChange("hi")
        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSearching).isFalse()
        assertThat(state.hits).isEmpty()
        assertThat(state.error).isNull()
    }

    @Test
    fun onQueryChange_updatesQueryAndClearsError() = runTest {
        val repository = FakeEmbeddingRepository().apply { error = RuntimeException("boom") }
        val viewModel = SearchViewModel(CalculateEmbeddingUseCase(repository), FakeDocumentRepository(), testDispatcher)
        viewModel.onQueryChange("hi")
        viewModel.onSearch()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.onQueryChange("hello")

        val state = viewModel.uiState.value
        assertThat(state.query).isEqualTo("hello")
        assertThat(state.error).isNull()
    }

    private fun viewModel(documents: FakeDocumentRepository = FakeDocumentRepository()) =
        SearchViewModel(
            CalculateEmbeddingUseCase(FakeEmbeddingRepository()),
            documents,
            testDispatcher,
        )

    private class FakeEmbeddingRepository : org.ggomes.myleo.vectorsearch.onnx.data.EmbeddingRepository {
        var vector: List<Float> = listOf(0.6f, 0.8f)
        var error: Throwable? = null

        override fun embedText(text: String): Result<List<Float>> =
            error?.let { Result.failure(it) } ?: Result.success(vector)
    }

    private class FakeDocumentRepository : DocumentRepository {
        var hits: List<ScoredChunk> = emptyList()
        var error: Throwable? = null
        var lastQuery: List<Float>? = null

        override fun ingest(document: String, chunks: List<ChunkEmbedding>): Result<Unit> =
            Result.success(Unit)

        override fun clear(): Result<Unit> = Result.success(Unit)

        override fun stats(): Result<CorpusStats> = Result.success(CorpusStats())

        override fun search(vector: List<Float>, topK: Int): Result<List<ScoredChunk>> {
            lastQuery = vector
            return error?.let { Result.failure(it) } ?: Result.success(hits)
        }
    }
}
