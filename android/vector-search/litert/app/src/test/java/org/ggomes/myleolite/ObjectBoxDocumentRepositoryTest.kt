package org.ggomes.myleolite

import org.ggomes.myleolite.data.ChunkEmbedding
import org.ggomes.myleolite.data.CorpusStats
import org.ggomes.myleolite.data.DocumentStat
import org.ggomes.myleolite.data.ObjectBoxDocumentRepository
import org.ggomes.myleolite.data.db.EMBEDDING_DIMENSIONS
import org.ggomes.myleolite.data.db.MyObjectBox
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within

/**
 * Real ObjectBox round-trip on the JVM (no mocks): ingest → stats → ANN search.
 * Vectors must be exactly [EMBEDDING_DIMENSIONS]-wide, like production ones.
 */
class ObjectBoxDocumentRepositoryTest {

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private lateinit var repository: ObjectBoxDocumentRepository
    private lateinit var store: io.objectbox.BoxStore

    @Before
    fun setUp() {
        store = MyObjectBox.builder()
            .directory(temporaryFolder.newFolder("objectbox"))
            .build()
        repository = ObjectBoxDocumentRepository(store)
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun search_afterIngest_returnsHitsOrderedBySimilarity() {
        val docA = "cats sit on mats"
        val docB = "quantum field theory"
        repository.ingest("Document 1", listOf(ChunkEmbedding(docA, hot(0)))).getOrThrow()
        repository.ingest("Document 2", listOf(ChunkEmbedding(docB, hot(1)))).getOrThrow()

        val hits = repository.search(hot(0), topK = 5).getOrThrow()

        assertThat(hits.map { it.text }).containsExactly(docA, docB)
        assertThat(hits.first().similarity).isCloseTo(1.0, within(1e-6))
        assertThat(hits[1].similarity).isLessThan(hits.first().similarity)
    }

    @Test
    fun stats_countsDocumentsAndChunks() {
        repository.ingest("Document 1", listOf(ChunkEmbedding("a", hot(0)), ChunkEmbedding("b", hot(1)))).getOrThrow()
        repository.ingest("Document 2", listOf(ChunkEmbedding("c", hot(2)))).getOrThrow()

        assertThat(repository.stats().getOrThrow()).isEqualTo(CorpusStats(documents = 2, chunks = 3))
    }

    @Test
    fun documentStats_perDocumentCounts() {
        repository.ingest("Document 1", listOf(ChunkEmbedding("a", hot(0)), ChunkEmbedding("b", hot(1)))).getOrThrow()
        repository.ingest("Document 2", listOf(ChunkEmbedding("c", hot(2)))).getOrThrow()

        assertThat(repository.documentStats().getOrThrow()).containsExactly(
            DocumentStat("Document 1", 2),
            DocumentStat("Document 2", 1),
        )
    }

    @Test
    fun search_emptyIndex_returnsEmpty() {
        assertThat(repository.search(hot(0)).getOrThrow()).isEmpty()
        assertThat(repository.stats().getOrThrow()).isEqualTo(CorpusStats())
    }

    @Test
    fun clear_emptiesIndex() {
        repository.ingest("Document 1", listOf(ChunkEmbedding("old", hot(0)))).getOrThrow()

        repository.clear().getOrThrow()

        assertThat(repository.search(hot(0)).getOrThrow()).isEmpty()
        assertThat(repository.stats().getOrThrow()).isEqualTo(CorpusStats())
    }

    /** 512-wide vector with 1s at [hot] positions, rest 0. */
    private fun hot(vararg hot: Int): List<Float> =
        FloatArray(EMBEDDING_DIMENSIONS.toInt())
            .also { v -> hot.forEach { v[it] = 1f } }
            .toList()
}
