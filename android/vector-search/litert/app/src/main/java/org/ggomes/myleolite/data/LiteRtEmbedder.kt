package org.ggomes.myleolite.data

import org.ggomes.myleolite.domain.Embedder
import org.ggomes.myleolite.domain.TextTokenizer
import kotlin.math.sqrt

/**
 * Raw LiteRT inference seam. Production code uses [CompiledModelRunner]; unit
 * tests use a fake. Keeps LiteRT (native) classes out of unit tests entirely.
 */
interface InferenceRunner : AutoCloseable {
    /** Runs one `[1 x seqLen]` INT32 triple and returns the raw embedding vector. */
    fun runEmbedding(ids: IntArray, mask: IntArray, segmentIds: IntArray): FloatArray
}

/**
 * [Embedder] over raw LiteRT: tokenize → run → L2-normalize (F-VS-2).
 *
 * The output is always L2-normalized so cosine similarity reduces to a dot product.
 */
class LiteRtEmbedder(
    private val runner: InferenceRunner,
    private val tokenizer: TextTokenizer,
    private val maxSeqLen: Int = DEFAULT_MAX_SEQ_LEN,
) : Embedder, AutoCloseable {

    override fun embed(text: String): List<Float> {
        val input = tokenizer.tokenize(text, maxSeqLen)
        val raw = runner.runEmbedding(input.ids, input.mask, input.segmentIds)
        return l2Normalize(raw).toList()
    }

    override fun close() = runner.close()

    companion object {
        const val DEFAULT_MAX_SEQ_LEN = 128
    }
}

/** L2-normalizes [v]; a zero vector is returned unchanged (avoids divide-by-zero). */
internal fun l2Normalize(v: FloatArray): FloatArray {
    var sum = 0.0
    for (x in v) sum += (x * x).toDouble()
    val norm = sqrt(sum).toFloat()
    if (norm == 0f) return v.copyOf()
    return FloatArray(v.size) { v[it] / norm }
}
