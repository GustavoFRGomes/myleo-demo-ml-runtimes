package org.ggomes.myleo.decision.dual.data

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.ggomes.myleo.decision.dual.domain.DecisionQuestion
import org.junit.Test

class JuliaDecoderTest {

    @Test
    fun softmax_uniformLogits_areUniform() {
        assertThat(JuliaDecoder.softmax(doubleArrayOf(0.0, 0.0)))
            .containsExactly(0.5, 0.5)
    }

    @Test
    fun softmax_sumIsOne_andShiftingLogitsDoesNotChangeProbabilities() {
        val probabilities = JuliaDecoder.softmax(doubleArrayOf(1.0, 2.0, 3.0))
        val shifted = JuliaDecoder.softmax(doubleArrayOf(101.0, 102.0, 103.0))

        assertThat(probabilities.sum()).isCloseTo(1.0, within(1e-12))
        for (index in probabilities.indices) {
            assertThat(shifted[index]).isCloseTo(probabilities[index], within(1e-15))
        }
    }

    @Test
    fun softmax_nonFiniteLogits_throw() {
        assertThatThrownBy { JuliaDecoder.softmax(doubleArrayOf(0.0, Double.NaN)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun gather_readsTheMarkerPositions() {
        val gathered = JuliaDecoder.gather(floatArrayOf(0f, 1f, 2f, 3f), intArrayOf(1, 3))

        assertThat(gathered).containsExactly(1.0, 3.0)
    }

    @Test
    fun gather_markerOutsideLogits_throws() {
        assertThatThrownBy { JuliaDecoder.gather(floatArrayOf(0f, 1f), intArrayOf(2)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun decode_choice_returnsTheHighestProbabilityId() {
        val question =
            DecisionQuestion.choice(
                "Which team?",
                listOf("billing" to "Billing", "shipping" to "Shipping", "access" to "Access"),
            )

        val answer = JuliaDecoder.decode(doubleArrayOf(0.1, 0.7, 0.2), question)

        assertThat(answer.choiceId).isEqualTo("shipping")
        assertThat(answer.argmax).isEqualTo(1)
        assertThat(answer.maxProbability).isCloseTo(answer.probabilities.max(), within(1e-15))
        assertThat(answer.score).isNull()
        assertThat(answer.noul).isNull()
    }

    @Test
    fun decode_choice_tieKeepsTheFirstOption() {
        val question = DecisionQuestion.choice("q", listOf("a" to "A", "b" to "B"))

        val answer = JuliaDecoder.decode(doubleArrayOf(0.0, 0.0), question)

        assertThat(answer.choiceId).isEqualTo("a")
    }

    @Test
    fun decode_score_returnsTheExpectedZeroBasedIndex() {
        val question = DecisionQuestion.score("How urgent?", listOf("none", "low", "high"))

        val answer = JuliaDecoder.decode(doubleArrayOf(0.0, 0.0, 0.0), question)

        // Uniform over 0,1,2 -> expectation 1.0.
        assertThat(answer.score).isCloseTo(1.0, within(1e-12))
        assertThat(answer.choiceId).isNull()
        assertThat(answer.noul).isNull()
    }

    @Test
    fun decode_noul_returnsProbabilityOfTrue() {
        val question = DecisionQuestion.noul("Refund?")

        val answer = JuliaDecoder.decode(doubleArrayOf(0.0, 0.0), question)

        assertThat(answer.noul).isCloseTo(0.5, within(1e-12))
        assertThat(answer.maxProbability).isNull()
        assertThat(answer.choiceId).isNull()
    }

    @Test
    fun decode_probabilityCountMismatch_throws() {
        val question = DecisionQuestion.noul("Refund?")

        assertThatThrownBy { JuliaDecoder.decode(doubleArrayOf(0.0, 0.0, 0.0), question) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
