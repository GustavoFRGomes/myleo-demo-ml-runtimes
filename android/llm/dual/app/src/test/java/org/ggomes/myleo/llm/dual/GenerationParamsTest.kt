package org.ggomes.myleo.llm.dual

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.ggomes.myleo.llm.dual.domain.GenerationParams
import org.junit.Test

class GenerationParamsTest {

    @Test
    fun validate_defaultParams_doesNotThrow() {
        GenerationParams.Default.validate()
    }

    @Test
    fun validate_negativeTemperature_throws() {
        assertThatThrownBy { GenerationParams(temperature = -0.1).validate() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun validate_zeroTopK_throws() {
        assertThatThrownBy { GenerationParams(topK = 0).validate() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun validate_topPOutOfRange_throws() {
        assertThatThrownBy { GenerationParams(topP = 1.5).validate() }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun clamped_bringsEveryFieldIntoRange() {
        val clamped = GenerationParams(temperature = -5.0, topK = 0, topP = 9.0, maxTokens = 100_000).clamped()

        assertThat(clamped.temperature).isEqualTo(0.0)
        assertThat(clamped.topK).isEqualTo(1)
        assertThat(clamped.topP).isEqualTo(1.0)
        assertThat(clamped.maxTokens).isEqualTo(4096)
        clamped.validate()
    }
}
