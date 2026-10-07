package org.ggomes.myleo.llm.dual

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.ggomes.myleo.llm.dual.domain.GenerationMetrics
import org.junit.Test

class GenerationMetricsTest {

    @Test
    fun tokensPerSecond_usesDecodeWindowOnly() {
        // TTFT 200 ms, total 1200 ms → 1000 ms decode for 10 tokens = 10 tok/s.
        val metrics = GenerationMetrics(ttftMs = 200, totalMs = 1200, generatedTokens = 10)
        assertThat(metrics.tokensPerSecond).isCloseTo(10.0, within(1e-9))
    }

    @Test
    fun tokensPerSecond_zeroTokens_isZero() {
        assertThat(GenerationMetrics(ttftMs = 100, totalMs = 500, generatedTokens = 0).tokensPerSecond)
            .isEqualTo(0.0)
    }

    @Test
    fun tokensPerSecond_noDecodeWindow_isZero() {
        assertThat(GenerationMetrics(ttftMs = 500, totalMs = 500, generatedTokens = 3).tokensPerSecond)
            .isEqualTo(0.0)
    }
}
