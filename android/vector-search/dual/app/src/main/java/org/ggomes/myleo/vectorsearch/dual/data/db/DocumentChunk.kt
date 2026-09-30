package org.ggomes.myleo.vectorsearch.dual.data.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

/**
 * Embedding width of the shared index field. Both runtimes embed the same
 * MiniLM-L6-v2 weights (`.tflite` / `.onnx`), so both natively produce 384-d
 * vectors — no padding, no truncation.
 */
const val EMBEDDING_DIMENSIONS = 384L

/**
 * One ingested corpus chunk + its embedding, indexed for ANN search.
 * Embeddings are L2-normalized, so COSINE distance orders by semantic similarity.
 * [document] groups chunks of one ingest run (e.g. "Document 3" / "Email 3").
 * [source] is the full parent item the chunk came from (email raw string or the
 * whole free-text corpus) so hits can open their source in the detail screen.
 */
@Entity
data class DocumentChunk(
    @Id var id: Long = 0,
    var document: String = "",
    var text: String = "",
    var source: String = "",
    @HnswIndex(dimensions = EMBEDDING_DIMENSIONS, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null,
)
