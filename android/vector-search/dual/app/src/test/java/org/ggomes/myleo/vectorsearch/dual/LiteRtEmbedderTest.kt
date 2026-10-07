package org.ggomes.myleo.vectorsearch.dual

import org.ggomes.myleo.vectorsearch.dual.data.InferenceRunner
import org.ggomes.myleo.vectorsearch.dual.data.LiteRtEmbedder
import org.ggomes.myleo.vectorsearch.dual.data.l2Normalize
import org.ggomes.myleo.vectorsearch.dual.domain.TextTokenizer
import org.ggomes.myleo.vectorsearch.dual.domain.TokenizedInput
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import kotlin.math.sqrt

class LiteRtEmbedderTest {

    @Test
    fun embed_validText_returnsL2NormalizedVector() {
        val embedder = LiteRtEmbedder(FakeRunner(floatArrayOf(3f, 4f)), FakeTokenizer())

        val result = embedder.embed("hello").toFloatArray()

        var norm = 0.0
        for (x in result) norm += (x * x).toDouble()
        assertThat(sqrt(norm)).isCloseTo(1.0, within(1e-6))
        assertThat(result[0].toDouble()).isCloseTo(0.6, within(1e-6))
        assertThat(result[1].toDouble()).isCloseTo(0.8, within(1e-6))
    }

    @Test
    fun embed_validText_forwardsTokenArraysToRunner() {
        val runner = FakeRunner(floatArrayOf(1f))
        val embedder = LiteRtEmbedder(runner, FakeTokenizer())

        embedder.embed("hi")

        assertThat(runner.lastIds!!.toList()).containsExactly(101, 102)
        assertThat(runner.lastMask!!.toList()).containsExactly(1, 1)
        assertThat(runner.lastSegmentIds!!.toList()).containsExactly(0, 0)
    }

    @Test
    fun embed_zeroVector_returnsWithoutNaN() {
        val embedder = LiteRtEmbedder(FakeRunner(floatArrayOf(0f, 0f)), FakeTokenizer())

        val result = embedder.embed("hi")

        assertThat(result).containsExactly(0f, 0f)
    }

    @Test
    fun close_delegatesToRunner() {
        val runner = FakeRunner(floatArrayOf(1f))
        val embedder = LiteRtEmbedder(runner, FakeTokenizer())

        embedder.close()

        assertThat(runner.closed).isTrue()
    }

    @Test
    fun l2Normalize_unitVector_returnsUnchanged() {
        assertThat(l2Normalize(floatArrayOf(1f, 0f)).toList()).containsExactly(1f, 0f)
    }

    private class FakeRunner(private val vector: FloatArray) : InferenceRunner {
        var lastIds: IntArray? = null
        var lastMask: IntArray? = null
        var lastSegmentIds: IntArray? = null
        var closed = false

        override fun runEmbedding(ids: IntArray, mask: IntArray, segmentIds: IntArray): FloatArray {
            lastIds = ids
            lastMask = mask
            lastSegmentIds = segmentIds
            return vector
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeTokenizer : TextTokenizer {
        override fun tokenize(text: String, maxSeqLen: Int): TokenizedInput =
            TokenizedInput(
                ids = intArrayOf(101, 102),
                mask = intArrayOf(1, 1),
                segmentIds = intArrayOf(0, 0),
            )
    }
}
