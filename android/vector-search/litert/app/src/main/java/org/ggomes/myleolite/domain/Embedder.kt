package org.ggomes.myleolite.domain

/**
 * Narrow inference-engine boundary for text embeddings.
 *
 * Implementations wrap a runtime SDK (e.g. a LiteRT Text Embedder) and must
 * return the L2-normalized embedding vector for [text]. Kept synchronous on
 * purpose: callers decide the dispatcher. Mocked in unit tests — never load a
 * real model there.
 */
interface Embedder {
    fun embed(text: String): List<Float>
}
