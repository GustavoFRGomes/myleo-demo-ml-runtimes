package org.ggomes.myleo.decision.dual.data

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.ggomes.myleo.decision.dual.domain.DecisionQuestion
import org.ggomes.myleo.decision.dual.domain.QuestionType
import org.junit.Test

class JuliaSequenceBuilderTest {

    /** Deterministic stand-in for the BPE tokenizer: 1 id per word, 2 ids for the state. */
    private class StubEncoder(
        private val idsFor: (String) -> IntArray = { text -> if (text == STATE) intArrayOf(20, 21) else intArrayOf(9) },
    ) : JuliaEncoder {
        override fun encode(text: String): IntArray = idsFor(text)
    }

    private val question = DecisionQuestion.choice("q", listOf("a" to "A", "b" to "B"))

    @Test
    fun build_placesAMaskBeforeEachOption_andEndsWithTheStateAndSeparator() {
        val encoder =
            object : JuliaEncoder {
                override fun encode(text: String): IntArray =
                    when (text) {
                        STATE -> intArrayOf(20, 21)
                        " A", " B" -> intArrayOf(10)
                        else -> intArrayOf(9)
                    }
            }

        val sequence = JuliaSequenceBuilder(encoder, window = 512).build(STATE, question)

        assertThat(sequence.ids.toList()).containsExactly(2, 9, 1, 4, 10, 4, 10, 1, 20, 21, 1)
        assertThat(sequence.markers.toList()).containsExactly(3, 5)
        assertThat(sequence.qtype).isEqualTo(QuestionType.CHOICE.qtype)
        assertThat(sequence.tokenCount).isEqualTo(11)
    }

    @Test
    fun build_qtypeFollowsTheQuestionType() {
        val choice = JuliaSequenceBuilder(StubEncoder()).build(STATE, question)
        val score =
            JuliaSequenceBuilder(StubEncoder()).build(
                STATE,
                DecisionQuestion.score("q", listOf("a", "b", "c")),
            )
        val noul = JuliaSequenceBuilder(StubEncoder()).build(STATE, DecisionQuestion.noul("q"))

        assertThat(choice.qtype).isEqualTo(0)
        assertThat(score.qtype).isEqualTo(1)
        assertThat(noul.qtype).isEqualTo(2)
    }

    @Test
    fun build_rejectsTheReservedMaskLiteralAnywhereInTheRequest() {
        assertThatThrownBy {
            JuliaSequenceBuilder(StubEncoder()).build("a <mask> b", question)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Reserved model marker")
    }

    @Test
    fun build_optionOver48Tokens_isRejected() {
        val encoder =
            object : JuliaEncoder {
                override fun encode(text: String): IntArray =
                    if (text.startsWith(" ")) IntArray(49) { 7 } else intArrayOf(9)
            }

        assertThatThrownBy {
            JuliaSequenceBuilder(encoder, window = 512).build(STATE, question)
        }.isInstanceOf(EncodingException::class.java)
            .hasMessageContaining("48-token")
    }

    @Test
    fun build_stateLargerThanTheWindow_isRejectedWithoutTruncation() {
        val encoder =
            object : JuliaEncoder {
                override fun encode(text: String): IntArray =
                    if (text == STATE) IntArray(600) { 7 } else intArrayOf(9)
            }

        assertThatThrownBy {
            JuliaSequenceBuilder(encoder, window = 512).build(STATE, question)
        }.isInstanceOf(EncodingException::class.java)
            .hasMessageContaining("the window is 512")
    }

    @Test
    fun build_optionListOverTheHeadBudget_isRejected() {
        val encoder =
            object : JuliaEncoder {
                override fun encode(text: String): IntArray =
                    if (text.startsWith(" ")) IntArray(48) { 7 } else intArrayOf(9)
            }
        val wide =
            DecisionQuestion.choice(
                "q",
                (1..11).map { "o$it" to "option $it" },
            )

        assertThatThrownBy {
            JuliaSequenceBuilder(encoder, window = 512).build(STATE, wide)
        }.isInstanceOf(EncodingException::class.java)
            .hasMessageContaining("head budget")
    }

    @Test
    fun build_sameRequestAtS1024_keepsTheIds() {
        val encoder = StubEncoder()

        val small = JuliaSequenceBuilder(encoder, window = 512).build(STATE, question)
        val large = JuliaSequenceBuilder(encoder, window = 1024).build(STATE, question)

        assertThat(large.ids.toList()).containsExactly(*small.ids.toList().toTypedArray())
        assertThat(large.markers.toList()).containsExactly(*small.markers.toList().toTypedArray())
    }

    @Test
    fun init_headBudgetAtOrAboveTheWindow_isRejected() {
        assertThatThrownBy { JuliaSequenceBuilder(StubEncoder(), window = 512, headLength = 508) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private companion object {
        const val STATE = "state"
    }
}
