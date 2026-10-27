package org.ggomes.myleo.decision.litert.ui.decision

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.ggomes.myleo.decision.litert.domain.DecisionQuestion
import org.ggomes.myleo.decision.litert.domain.QuestionType
import org.junit.Test

class QuestionDraftTest {

    @Test
    fun normalized_choiceBlankIdsGetAPositionalFallback() {
        val draft =
            QuestionDraft(
                QuestionType.CHOICE,
                "Which team?",
                listOf(OptionDraft("", "Billing"), OptionDraft("", "Shipping")),
            )

        assertThat(draft.normalized().options.map { it.id }).containsExactly("o1", "o2")
    }

    @Test
    fun normalized_choiceKeepsPresetIds() {
        val draft =
            QuestionDraft(
                QuestionType.CHOICE,
                "Which team?",
                listOf(OptionDraft("billing", "Billing"), OptionDraft("shipping", "Shipping")),
            )

        assertThat(draft.normalized().options.map { it.id }).containsExactly("billing", "shipping")
        assertThat(draft.toQuestion().options.map { it.id }).containsExactly("billing", "shipping")
    }

    @Test
    fun normalized_scoreIdsAreZeroBasedIndices() {
        val draft =
            QuestionDraft(
                QuestionType.SCORE,
                "How urgent?",
                listOf(OptionDraft("x", "none"), OptionDraft("x", "high")),
            )

        assertThat(draft.normalized().options.map { it.id }).containsExactly("0", "1")
    }

    @Test
    fun normalized_noulPinsFalseThenTrueAndToleratesAMissingOption() {
        val draft =
            QuestionDraft(
                QuestionType.NOUL,
                "Refund?",
                listOf(OptionDraft("whatever", "No refund")),
            )
        val normalized = draft.normalized()

        assertThat(normalized.options.map { it.id }).containsExactly("false", "true")
        assertThat(normalized.options.map { it.text }).containsExactly("No refund", "")
    }

    @Test
    fun of_roundTripsAPresetQuestion() {
        val question = DecisionQuestion.score("How urgent?", listOf("none", "low", "high"))

        val draft = QuestionDraft.of(question)

        assertThat(draft.type).isEqualTo(QuestionType.SCORE)
        assertThat(draft.options.map { it.text }).containsExactly("none", "low", "high")
        assertThat(draft.toQuestion().options.map { it.id }).containsExactly("0", "1", "2")
    }

    @Test
    fun toQuestion_blankDescription_throwsForValidation() {
        val draft =
            QuestionDraft(
                QuestionType.CHOICE,
                "Which team?",
                listOf(OptionDraft("o1", "Billing"), OptionDraft("o2", "")),
            )

        assertThatThrownBy { draft.toQuestion() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("option")
    }

    @Test
    fun default_everyTypeHasAUsableShape() {
        QuestionType.entries.forEach { type ->
            val draft = QuestionDraft.default(type)

            assertThat(draft.type).isEqualTo(type)
            assertThat(draft.options).hasSizeGreaterThanOrEqualTo(2)
            if (type == QuestionType.NOUL) {
                assertThat(draft.options.map { it.id }).containsExactly("false", "true")
            }
        }
    }
}
