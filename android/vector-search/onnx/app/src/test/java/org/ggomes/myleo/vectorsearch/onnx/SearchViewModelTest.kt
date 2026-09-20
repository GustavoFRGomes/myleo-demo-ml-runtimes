package org.ggomes.myleo.vectorsearch.onnx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ggomes.myleo.vectorsearch.onnx.data.EmbeddingRepository
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
    fun onSearch_validQuery_publishesEmbeddingHit() = runTest {
        val repository = FakeEmbeddingRepository().apply { vector = listOf(0.6f, 0.8f) }
        val viewModel = SearchViewModel(CalculateEmbeddingUseCase(repository), testDispatcher)

        viewModel.onQueryChange("hi")
        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSearching).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.hits).hasSize(1)
        val hit = state.hits.first()
        assertThat(hit.text).isEqualTo("hi")
        assertThat(hit.dimensions).isEqualTo(2)
        assertThat(hit.embedMs).isGreaterThanOrEqualTo(0)
        assertThat(hit.preview).containsExactly(0.6f, 0.8f)
    }

    @Test
    fun onSearch_blankQuery_doesNothing() = runTest {
        val viewModel = SearchViewModel(
            CalculateEmbeddingUseCase(FakeEmbeddingRepository()),
            testDispatcher,
        )

        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSearching).isFalse()
        assertThat(state.hits).isEmpty()
        assertThat(state.error).isNull()
    }

    @Test
    fun onSearch_repositoryFailure_showsError() = runTest {
        val repository = FakeEmbeddingRepository().apply { error = RuntimeException("no model") }
        val viewModel = SearchViewModel(CalculateEmbeddingUseCase(repository), testDispatcher)

        viewModel.onQueryChange("hi")
        viewModel.onSearch()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSearching).isFalse()
        assertThat(state.hits).isEmpty()
        assertThat(state.error).isEqualTo("no model")
    }

    @Test
    fun onQueryChange_updatesQueryAndClearsError() = runTest {
        val repository = FakeEmbeddingRepository().apply { error = RuntimeException("boom") }
        val viewModel = SearchViewModel(CalculateEmbeddingUseCase(repository), testDispatcher)
        viewModel.onQueryChange("hi")
        viewModel.onSearch()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.onQueryChange("hello")

        val state = viewModel.uiState.value
        assertThat(state.query).isEqualTo("hello")
        assertThat(state.error).isNull()
    }

    private class FakeEmbeddingRepository : EmbeddingRepository {
        var vector: List<Float> = emptyList()
        var error: Throwable? = null

        override fun embedText(text: String): Result<List<Float>> =
            error?.let { Result.failure(it) } ?: Result.success(vector)
    }
}
