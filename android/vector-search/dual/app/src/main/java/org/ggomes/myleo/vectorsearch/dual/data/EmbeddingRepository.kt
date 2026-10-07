package org.ggomes.myleo.vectorsearch.dual.data

import org.ggomes.myleo.vectorsearch.dual.domain.Embedder

/**
 * Repository boundary for embedding access. The ViewModel/UseCase layer talks
 * to this interface only — never to the inference SDK directly.
 */
interface EmbeddingRepository {
    fun embedText(text: String): Result<List<Float>>
}

/** Delegates to the injected [Embedder] and converts throws into [Result]. */
class DefaultEmbeddingRepository(
    private val embedder: Embedder,
) : EmbeddingRepository {
    override fun embedText(text: String): Result<List<Float>> =
        runCatching { embedder.embed(text) }
}
