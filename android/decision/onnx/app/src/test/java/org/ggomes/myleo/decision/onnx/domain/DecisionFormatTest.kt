package org.ggomes.myleo.decision.onnx.domain

import org.assertj.core.api.Assertions.assertThat
import org.ggomes.myleo.decision.onnx.FakeDecider
import org.junit.Test

class DecisionFormatTest {

    @Test
    fun percent_rendersOneDecimalAndIsLocaleStable() {
        assertThat(DecisionFormat.percent(0.5)).isEqualTo("50.0%")
        assertThat(DecisionFormat.percent(0.1234)).isEqualTo("12.3%")
        assertThat(DecisionFormat.percent(1.0)).isEqualTo("100.0%")
    }

    @Test
    fun millis_rendersWholeMilliseconds() {
        assertThat(DecisionFormat.millis(0.4)).isEqualTo("0 ms")
        assertThat(DecisionFormat.millis(80.7)).isEqualTo("81 ms")
    }

    @Test
    fun scoreValue_showsTheExpectedIndexOutOfTheHighestIndex() {
        assertThat(DecisionFormat.scoreValue(1.8234, 4)).isEqualTo("1.82 / 3")
    }

    @Test
    fun headline_choiceUsesTheWinningOptionText() {
        val question = DecisionQuestion.choice("q", listOf("billing" to "Billing", "shipping" to "Shipping"))
        val outcome =
            FakeDecider.outcome(
                question = question,
                probabilities = doubleArrayOf(0.2, 0.8),
                choiceId = "shipping",
                argmax = 1,
            )

        assertThat(DecisionFormat.headline(outcome)).isEqualTo("→ Shipping")
    }

    @Test
    fun headline_scoreUsesTheRubricExpectation() {
        val question = DecisionQuestion.score("q", listOf("none", "low", "high", "critical"))
        val outcome =
            FakeDecider.outcome(
                question = question,
                probabilities = doubleArrayOf(0.25, 0.25, 0.25, 0.25),
                score = 1.5,
            )

        assertThat(DecisionFormat.headline(outcome)).isEqualTo("1.50 / 3")
    }

    @Test
    fun headline_noulUsesTheProbabilityOfTrue() {
        val question = DecisionQuestion.noul("q")
        val outcome =
            FakeDecider.outcome(
                question = question,
                probabilities = doubleArrayOf(0.75, 0.25),
                noul = 0.25,
            )

        assertThat(DecisionFormat.headline(outcome)).isEqualTo("P(true) = 25.0%")
    }
}
