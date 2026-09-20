package org.ggomes.myleolite.data.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.VectorDistanceType

/** Embedding width every stored vector must have (bert_embedder outputs 512 floats). */
const val EMBEDDING_DIMENSIONS = 512L

/**
 * One ingested corpus chunk + its embedding, indexed for ANN search.
 * Embeddings are L2-normalized, so COSINE distance orders by semantic similarity.
 * [document] groups chunks of one ingest run (e.g. "Document 3").
 */
@Entity
data class DocumentChunk(
    @Id var id: Long = 0,
    var document: String = "",
    var text: String = "",
    @HnswIndex(dimensions = EMBEDDING_DIMENSIONS, distanceType = VectorDistanceType.COSINE)
    var embedding: FloatArray? = null,
)
