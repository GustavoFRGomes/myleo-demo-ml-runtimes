package org.ggomes.myleo.vectorsearch.dual.data

import org.ggomes.myleo.vectorsearch.dual.domain.Embedder
import org.ggomes.myleo.vectorsearch.dual.domain.TextTokenizer

/**
 * [Embedder] over ONNX Runtime: tokenize → run → mean-pool → L2-normalize (F-VS-2).
 *
 * The output is always L2-normalized so cosine similarity reduces to a dot product.
 */
class OnnxEmbedder(
    private val runner: OnnxSessionRunner,
    private val tokenizer: TextTokenizer,
    private val maxSeqLen: Int = DEFAULT_MAX_SEQ_LEN,
) : Embedder, AutoCloseable {

    override fun embed(text: String): List<Float> {
        val input = tokenizer.tokenize(text, maxSeqLen)
        val hidden = runner.runHidden(
            input.ids.map { it.toLong() }.toLongArray(),
            input.mask.map { it.toLong() }.toLongArray(),
            input.segmentIds.map { it.toLong() }.toLongArray(),
        )
        val dim = hidden.size / maxSeqLen
        require(dim > 0 && hidden.size == maxSeqLen * dim) {
            "unexpected hidden size ${hidden.size} for $maxSeqLen tokens"
        }
        return l2Normalize(meanPool(hidden, maxSeqLen, dim, input.mask)).toList()
    }

    override fun close() = runner.close()

    companion object {
        const val DEFAULT_MAX_SEQ_LEN = 128
    }
}
