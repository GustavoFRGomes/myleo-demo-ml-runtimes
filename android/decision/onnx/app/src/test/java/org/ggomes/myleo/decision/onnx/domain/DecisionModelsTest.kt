package org.ggomes.myleo.decision.onnx.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class DecisionModelsTest {

    @Test
    fun question_rejectsFewerThanTwoOptions() {
        assertThatThrownBy {
            DecisionQuestion.choice("q", listOf("a" to "only"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("2-20")
    }

    @Test
    fun question_rejectsMoreThanTwentyOptions() {
        assertThatThrownBy {
            DecisionQuestion.choice("q", (1..21).map { "o$it" to "option $it" })
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("2-20")
    }

    @Test
    fun question_rejectsBlankInstructionsOrDescriptions() {
        assertThatThrownBy { DecisionQuestion.choice("  ", listOf("a" to "A", "b" to "B")) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DecisionQuestion.choice("q", listOf("a" to "", "b" to "B")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun question_rejectsDuplicateOptionIds() {
        assertThatThrownBy {
            DecisionQuestion.choice("q", listOf("a" to "A", "a" to "B"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("unique")
    }

    @Test
    fun score_derivesZeroBasedIdsFromTheRubric() {
        val question = DecisionQuestion.score("q", listOf("none", "low", "high"))

        assertThat(question.options.map { it.id }).containsExactly("0", "1", "2")
        assertThat(question.options.map { it.text }).containsExactly("none", "low", "high")
    }

    @Test
    fun noul_requiresExactlyFalseThenTrue() {
        val literal = DecisionQuestion.noul("q")

        assertThat(literal.type).isEqualTo(QuestionType.NOUL)
        assertThat(literal.options.map { it.id }).containsExactly("false", "true")
        assertThat(literal.options.map { it.text }).containsExactly("false", "true")

        assertThatThrownBy {
            DecisionQuestion(
                QuestionType.NOUL,
                "q",
                listOf(DecisionOption("true", "Yes"), DecisionOption("false", "No")),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("false, true")

        assertThatThrownBy {
            DecisionQuestion(
                QuestionType.NOUL,
                "q",
                listOf(
                    DecisionOption("false", "No"),
                    DecisionOption("true", "Yes"),
                    DecisionOption("maybe", "Maybe"),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun request_rejectsABlankState() {
        assertThatThrownBy {
            DecisionRequest("   ", DecisionQuestion.noul("q"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("state")
    }

    @Test
    fun outcome_rejectsOneProbabilityPerOption() {
        val question = DecisionQuestion.noul("q")

        assertThatThrownBy {
            FakeOutcomes.outcome(question, doubleArrayOf(0.5, 0.4, 0.1))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("one probability per option")
    }

    @Test
    fun timings_totalAddsEveryStage() {
        val timings = DecisionTimings(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0)

        assertThat(timings.graphMs).isEqualTo(15.0)
        assertThat(timings.totalMs).isEqualTo(28.0)
    }
}

/** Local outcome factory so the model test does not depend on the test-double package. */
private object FakeOutcomes {
    fun outcome(question: DecisionQuestion, probabilities: DoubleArray): DecisionOutcome =
        DecisionOutcome(
            question = question,
            probabilities = probabilities,
            choiceId = null,
            score = null,
            noul = 0.5,
            maxProbability = null,
            argmax = 0,
            tokenCount = 10,
            window = 512,
            accelerator = Accelerator.CPU,
            timings = DecisionTimings(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        )
}
