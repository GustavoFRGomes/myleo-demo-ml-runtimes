package org.ggomes.myleo.vectorsearch.dual

import org.ggomes.myleo.vectorsearch.dual.data.OnnxEmbedder
import org.ggomes.myleo.vectorsearch.dual.data.OnnxSessionRunner
import org.ggomes.myleo.vectorsearch.dual.data.l2Normalize
import org.ggomes.myleo.vectorsearch.dual.data.meanPool
import org.ggomes.myleo.vectorsearch.dual.domain.TextTokenizer
import org.ggomes.myleo.vectorsearch.dual.domain.TokenizedInput
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import kotlin.math.sqrt

class OnnxEmbedderTest {

    @Test
    fun meanPool_averagesMaskedPositions() {
        // seqLen=3, dim=2; token 2 masked out.
        val hidden = floatArrayOf(1f, 2f, 10f, 20f, 3f, 4f)
        val pooled = meanPool(hidden, seqLen = 3, dim = 2, mask = intArrayOf(1, 0, 1))
        assertThat(pooled[0]).isCloseTo(2f, within(1e-6f))
        assertThat(pooled[1]).isCloseTo(3f, within(1e-6f))
    }

    @Test
    fun meanPool_allMasked_returnsZeros() {
        val pooled = meanPool(floatArrayOf(5f, 6f), seqLen = 1, dim = 2, mask = intArrayOf(0))
        assertThat(pooled.toList()).containsExactly(0f, 0f)
    }

    @Test
    fun embed_tokenizesRunsMeanPoolsAndNormalizes() {
        val seqLen = 4
        val dim = 3
        val tokenizer = FakeTokenizer(seqLen)
        val hidden = FloatArray(seqLen * dim) { (it + 1).toFloat() }
        val embedder = OnnxEmbedder(FakeRunner(hidden), tokenizer, maxSeqLen = seqLen)

        val vector = embedder.embed("hello")

        // mask [1,1,0,0] → mean of first two token rows.
        val expected = l2Normalize(
            floatArrayOf(
                (hidden[0] + hidden[3]) / 2,
                (hidden[1] + hidden[4]) / 2,
                (hidden[2] + hidden[5]) / 2,
            ),
        )
        assertThat(vector).hasSize(dim)
        vector.forEachIndexed { i, v -> assertThat(v.toDouble()).isCloseTo(expected[i].toDouble(), within(1e-5)) }
        // L2-normalized.
        val norm = sqrt(vector.sumOf { (it * it).toDouble() })
        assertThat(norm).isCloseTo(1.0, within(1e-5))
    }

    @Test
    fun embed_shapeMismatch_throws() {
        val tokenizer = FakeTokenizer(seqLen = 4)
        val embedder = OnnxEmbedder(FakeRunner(FloatArray(5)), tokenizer, maxSeqLen = 4)
        try {
            embedder.embed("hello")
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("hidden size")
        }
    }

    private class FakeTokenizer(private val seqLen: Int) : TextTokenizer {
        override fun tokenize(text: String, maxSeqLen: Int): TokenizedInput =
            TokenizedInput(
                ids = IntArray(seqLen) { it + 10 },
                // First two positions active so mean-pool is deterministic.
                mask = IntArray(seqLen) { if (it < 2) 1 else 0 },
                segmentIds = IntArray(seqLen),
            )
    }

    private class FakeRunner(private val hidden: FloatArray) : OnnxSessionRunner {
        override fun runHidden(ids: LongArray, mask: LongArray, typeIds: LongArray): FloatArray = hidden
        override fun close() = Unit
    }
}
