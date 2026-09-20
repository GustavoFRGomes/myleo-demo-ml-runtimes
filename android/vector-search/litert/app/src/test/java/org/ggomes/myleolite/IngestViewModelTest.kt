package org.ggomes.myleolite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ggomes.myleolite.data.ChunkEmbedding
import org.ggomes.myleolite.data.CorpusStats
import org.ggomes.myleolite.data.DocumentRepository
import org.ggomes.myleolite.data.DocumentStat
import org.ggomes.myleolite.data.EmbeddingRepository
import org.ggomes.myleolite.data.ScoredChunk
import org.ggomes.myleolite.domain.CalculateEmbeddingUseCase
import org.ggomes.myleolite.domain.DeviceSnapshot
import org.ggomes.myleolite.domain.SystemStatsProvider
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
    fun onIngest_twoParagraphs_persistsChunksAndReportsTimings() = runTest {
        val documents = FakeDocumentRepository()
        val viewModel = IngestViewModel(CalculateEmbeddingUseCase(FakeEmbeddingRepository()), documents, testDispatcher)

        viewModel.onCorpusChange("first\n\nsecond")
        viewModel.onIngest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isIngesting).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.chunks.map { it.text }).containsExactly("first", "second")
        assertThat(state.stored).isTrue()
        assertThat(documents.ingested.keys).containsExactly("Document 1")
        assertThat(documents.ingested.values.flatten().map { it.text }).containsExactly("first", "second")
        val run = state.lastRun
        assertThat(run).isNotNull()
        assertThat(run!!.document).isEqualTo("Document 1")
        assertThat(run.chunks).isEqualTo(2)
        assertThat(run.embedMs).isGreaterThanOrEqualTo(0)
        assertThat(run.storeMs).isGreaterThanOrEqualTo(0)
        assertThat(run.totalMs).isGreaterThanOrEqualTo(run.embedMs + run.storeMs)
        assertThat(state.corpus).isEqualTo(CorpusStats(documents = 1, chunks = 2))
    }

    @Test
    fun onIngest_secondRun_labelsNextDocument() = runTest {
        val documents = FakeDocumentRepository()
        val viewModel = IngestViewModel(CalculateEmbeddingUseCase(FakeEmbeddingRepository()), documents, testDispatcher)

        viewModel.onCorpusChange("one")
        viewModel.onIngest()
        advanceUntilIdle()
        viewModel.onCorpusChange("two")
        viewModel.onIngest()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.lastRun?.document).isEqualTo("Document 2")
        assertThat(viewModel.uiState.value.corpus).isEqualTo(CorpusStats(documents = 2, chunks = 2))
    }

    @Test
    fun onIngest_blankCorpus_doesNothing() = runTest {
        val documents = FakeDocumentRepository()
        val viewModel = IngestViewModel(CalculateEmbeddingUseCase(FakeEmbeddingRepository()), documents, testDispatcher)

        viewModel.onIngest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isIngesting).isFalse()
        assertThat(state.chunks).isEmpty()
        assertThat(state.stored).isFalse()
        assertThat(state.lastRun).isNull()
        assertThat(documents.ingested).isEmpty()
    }

    @Test
    fun onIngest_chunkFailure_keepsSuccessesAndShowsError() = runTest {
        val repository = FakeEmbeddingRepository().apply { failOn = "bad" }
        val viewModel = IngestViewModel(CalculateEmbeddingUseCase(repository), FakeDocumentRepository(), testDispatcher)

        viewModel.onCorpusChange("good\n\nbad")
        viewModel.onIngest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isIngesting).isFalse()
        assertThat(state.chunks.map { it.text }).containsExactly("good")
        assertThat(state.error).isEqualTo("nope")
        assertThat(state.stored).isFalse()
    }

    @Test
    fun onIngest_persistFailure_showsError() = runTest {
        val documents = FakeDocumentRepository().apply { error = RuntimeException("disk full") }
        val viewModel = IngestViewModel(CalculateEmbeddingUseCase(FakeEmbeddingRepository()), documents, testDispatcher)

        viewModel.onCorpusChange("first")
        viewModel.onIngest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isIngesting).isFalse()
        assertThat(state.error).isEqualTo("disk full")
        assertThat(state.stored).isFalse()
    }

    @Test
    fun onClearIndex_clearsStateAndStats() = runTest {
        val documents = FakeDocumentRepository()
        val viewModel = IngestViewModel(CalculateEmbeddingUseCase(FakeEmbeddingRepository()), documents, testDispatcher)
        viewModel.onCorpusChange("first")
        viewModel.onIngest()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.corpus.chunks).isEqualTo(1)

        viewModel.onClearIndex()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.chunks).isEmpty()
        assertThat(state.stored).isFalse()
        assertThat(state.corpus).isEqualTo(CorpusStats())
    }

    @Test
    fun onIngest_recordsBatteryAndDocuments() = runTest {
        val viewModel = IngestViewModel(
            CalculateEmbeddingUseCase(FakeEmbeddingRepository()),
            FakeDocumentRepository(),
            modelName = "embedding.tflite",
            systemStats = FakeSystemStats(80),
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.modelName).isEqualTo("embedding.tflite")

        viewModel.onCorpusChange("first\n\nsecond")
        viewModel.onIngest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.lastRun?.batteryStartPct).isEqualTo(80)
        assertThat(state.lastRun?.batteryEndPct).isEqualTo(80)
        assertThat(state.documents).containsExactly(DocumentStat("Document 1", 2))
    }

    private class FakeSystemStats(private val percent: Int?) : SystemStatsProvider {
        override fun snapshot() =
            DeviceSnapshot("TestMan", "TestModel", onBattery = true, batteryPercent = percent)
    }

    private class FakeEmbeddingRepository : EmbeddingRepository {
        var failOn: String? = null

        override fun embedText(text: String): Result<List<Float>> =
            if (text == failOn) Result.failure(RuntimeException("nope"))
            else Result.success(listOf(0.6f, 0.8f))
    }

    private class FakeDocumentRepository : DocumentRepository {
        val ingested = mutableMapOf<String, List<ChunkEmbedding>>()
        var error: Throwable? = null

        override fun ingest(document: String, chunks: List<ChunkEmbedding>): Result<Unit> {
            error?.let { return Result.failure(it) }
            ingested[document] = chunks
            return Result.success(Unit)
        }

        override fun clear(): Result<Unit> {
            ingested.clear()
            return Result.success(Unit)
        }

        override fun stats(): Result<CorpusStats> =
            Result.success(CorpusStats(ingested.size, ingested.values.sumOf { it.size }))

        override fun documentStats(): Result<List<DocumentStat>> =
            Result.success(
                ingested.map { (label, chunks) -> DocumentStat(label, chunks.size) }
                    .sortedBy { it.label },
            )

        override fun search(vector: List<Float>, topK: Int): Result<List<ScoredChunk>> =
            Result.success(emptyList())
    }
}
