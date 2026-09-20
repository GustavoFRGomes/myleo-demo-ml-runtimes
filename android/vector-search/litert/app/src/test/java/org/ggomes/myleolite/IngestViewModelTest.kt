package org.ggomes.myleolite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ggomes.myleolite.data.EmbeddingRepository
import org.ggomes.myleolite.domain.CalculateEmbeddingUseCase
import org.ggomes.myleolite.ui.ingest.IngestViewModel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

@OptIn(ExperimentalCoroutinesApi::class)
class IngestViewModelTest {

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
    fun onIngest_twoParagraphs_publishesTwoChunks() = runTest {
        val viewModel = IngestViewModel(
            CalculateEmbeddingUseCase(FakeEmbeddingRepository()),
            testDispatcher,
        )

        viewModel.onCorpusChange("first\n\nsecond")
        viewModel.onIngest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isIngesting).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.chunks.map { it.text }).containsExactly("first", "second")
        assertThat(state.chunks.map { it.dimensions }).containsExactly(2, 2)
    }

    @Test
    fun onIngest_blankCorpus_doesNothing() = runTest {
        val viewModel = IngestViewModel(
            CalculateEmbeddingUseCase(FakeEmbeddingRepository()),
            testDispatcher,
        )

        viewModel.onIngest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isIngesting).isFalse()
        assertThat(state.chunks).isEmpty()
    }

    @Test
    fun onIngest_chunkFailure_keepsSuccessesAndShowsError() = runTest {
        val repository = FakeEmbeddingRepository().apply { failOn = "bad" }
        val viewModel = IngestViewModel(CalculateEmbeddingUseCase(repository), testDispatcher)

        viewModel.onCorpusChange("good\n\nbad")
        viewModel.onIngest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isIngesting).isFalse()
        assertThat(state.chunks.map { it.text }).containsExactly("good")
        assertThat(state.error).isEqualTo("nope")
    }

    private class FakeEmbeddingRepository : EmbeddingRepository {
        var failOn: String? = null

        override fun embedText(text: String): Result<List<Float>> =
            if (text == failOn) Result.failure(RuntimeException("nope"))
            else Result.success(listOf(0.6f, 0.8f))
    }
}
