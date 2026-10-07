package org.ggomes.myleo.vectorsearch.onnx.data

import io.objectbox.BoxStore
import org.ggomes.myleo.vectorsearch.onnx.data.db.DocumentChunk
import org.ggomes.myleo.vectorsearch.onnx.data.db.DocumentChunk_

/** Chunk text paired with its computed embedding, ready to persist. */
data class ChunkEmbedding(
    val text: String,
    val vector: List<Float>,
    /** Full parent item the chunk came from (email raw string or whole corpus). */
    val source: String = text,
)

/** A ranked search hit. [similarity] is cosine similarity in 0..1 (higher = closer). */
data class ScoredChunk(
    val text: String,
    val similarity: Double,
    /** Full parent item the chunk came from, for the detail screen. */
    val source: String = text,
)

/** Live corpus totals for the performance report. */
data class CorpusStats(
    val documents: Int = 0,
    val chunks: Int = 0,
)

/** Per-document chunk counts for the performance report. */
data class DocumentStat(
    val label: String,
    val chunks: Int,
)

/** Persistence + ANN-search boundary. The ViewModel layer talks to this only. */
interface DocumentRepository {
    /** Appends [chunks] under [document] (re-ingesting the same text duplicates it). */
    fun ingest(document: String, chunks: List<ChunkEmbedding>): Result<Unit>

    /** Deletes the whole corpus. */
    fun clear(): Result<Unit>

    /** Live totals: distinct documents + stored chunks. */
    fun stats(): Result<CorpusStats>

    /** Chunk counts per document label, sorted by label. */
    fun documentStats(): Result<List<DocumentStat>>

    /** Top-[topK] chunks by cosine similarity to [vector]. Empty when the index is empty. */
    fun search(vector: List<Float>, topK: Int = DEFAULT_TOP_K): Result<List<ScoredChunk>>

    companion object {
        const val DEFAULT_TOP_K = 5
    }
}

/** [DocumentRepository] over ObjectBox HNSW vector search (F-VS-3, F-VS-4). */
class ObjectBoxDocumentRepository(private val store: BoxStore) : DocumentRepository {

    override fun ingest(document: String, chunks: List<ChunkEmbedding>): Result<Unit> = runCatching {
        val box = store.boxFor(DocumentChunk::class.java)
        box.put(
            chunks.map {
                DocumentChunk(document = document, text = it.text, source = it.source, embedding = it.vector.toFloatArray())
            },
        )
    }

    override fun clear(): Result<Unit> = runCatching {
        store.boxFor(DocumentChunk::class.java).removeAll()
    }

    override fun stats(): Result<CorpusStats> = runCatching {
        val box = store.boxFor(DocumentChunk::class.java)
        val documents = box.query().build().use { query ->
            query.find().map { it.document }.distinct().size
        }
        CorpusStats(documents = documents, chunks = box.count().toInt())
    }

    override fun documentStats(): Result<List<DocumentStat>> = runCatching {
        store.boxFor(DocumentChunk::class.java).query().build().use { query ->
            query.find().groupBy { it.document }
                .map { (label, chunks) -> DocumentStat(label, chunks.size) }
                .sortedBy { it.label }
        }
    }

    override fun search(vector: List<Float>, topK: Int): Result<List<ScoredChunk>> = runCatching {
        val box = store.boxFor(DocumentChunk::class.java)
        box.query(DocumentChunk_.embedding.nearestNeighbors(vector.toFloatArray(), topK))
            .build()
            .use { query ->
                // ObjectBox COSINE score is a distance (0 = identical); convert to similarity.
                query.findWithScores().map {
                    val chunk = it.get()
                    ScoredChunk(chunk.text, (1.0 - it.score).coerceIn(0.0, 1.0), chunk.source)
                }
            }
    }
}
