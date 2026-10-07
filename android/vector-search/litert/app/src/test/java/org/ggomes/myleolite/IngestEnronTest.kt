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
import org.ggomes.myleolite.domain.EmailDatasetLoader
import org.ggomes.myleolite.ui.ingest.IngestSource
import org.ggomes.myleolite.ui.ingest.IngestViewModel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

@OptIn(ExperimentalCoroutinesApi::class)
class IngestEnronTest {

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
    fun onIngest_enronSample_eachEmailBecomesOwnDocumentWithSource() = runTest {
        val documents = FakeDocumentRepository()
        val loader = FakeLoader(listOf("mail one body", "mail two body"))
        val viewModel = IngestViewModel(
            CalculateEmbeddingUseCase(FakeEmbeddingRepository()),
            documents,
            testDispatcher,
            datasetLoader = loader,
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.availableItems).isEqualTo(2)

        viewModel.onSourceChange(IngestSource.ENRON_SAMPLE)
        viewModel.onItemCountChange(2)
        viewModel.onIngest()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isIngesting).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.stored).isTrue()
        assertThat(documents.ingested.keys).containsExactly("Email 1", "Email 2")
        assertThat(documents.ingested.values.flatten().map { it.source })
            .containsExactly("mail one body", "mail two body")
        assertThat(state.lastRun?.document).isEqualTo("Emails 1-2")
    }

    @Test
    fun onIngest_enronWithoutLoader_showsError() = runTest {
        val viewModel = IngestViewModel(
            CalculateEmbeddingUseCase(FakeEmbeddingRepository()),
            FakeDocumentRepository(),
            testDispatcher,
            datasetLoader = null,
        )
        viewModel.onSourceChange(IngestSource.ENRON_SAMPLE)
        viewModel.onIngest()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("no dataset loader wired")
    }

    @Test
    fun onSourceChange_updatesSource() = runTest {
        val viewModel = IngestViewModel(
            CalculateEmbeddingUseCase(FakeEmbeddingRepository()),
            FakeDocumentRepository(),
            testDispatcher,
        )
        viewModel.onSourceChange(IngestSource.ENRON_SAMPLE)
        assertThat(viewModel.uiState.value.source).isEqualTo(IngestSource.ENRON_SAMPLE)
        viewModel.onItemCountChange(10)
        assertThat(viewModel.uiState.value.itemCount).isEqualTo(10)
    }

    private class FakeLoader(private val emails: List<String>) : EmailDatasetLoader {
        override fun availableEmails(): Int = emails.size
        override fun loadEmails(count: Int): List<String> = emails.take(count)
    }

    private class FakeEmbeddingRepository : EmbeddingRepository {
        override fun embedText(text: String): Result<List<Float>> =
            Result.success(listOf(0.6f, 0.8f))
    }

    private class FakeDocumentRepository : DocumentRepository {
        val ingested = mutableMapOf<String, List<ChunkEmbedding>>()

        override fun ingest(document: String, chunks: List<ChunkEmbedding>): Result<Unit> {
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
