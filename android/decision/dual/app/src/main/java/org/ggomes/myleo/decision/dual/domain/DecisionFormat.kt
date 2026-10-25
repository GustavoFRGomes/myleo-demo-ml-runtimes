package org.ggomes.myleo.decision.dual.domain

import java.util.Locale

/** Presentation helpers shared by the screen and its tests; all output is locale-stable. */
object DecisionFormat {
    fun percent(value: Double): String = String.format(Locale.US, "%.1f%%", value * 100)

    fun millis(value: Double): String = String.format(Locale.US, "%.0f ms", value)

    /** `1.82 / 3` for a 4-option rubric: the expected index out of the highest index. */
    fun scoreValue(score: Double, optionCount: Int): String =
        String.format(Locale.US, "%.2f / %d", score, optionCount - 1)

    /** One-line answer in the author's named-question terms. */
    fun headline(outcome: DecisionOutcome): String =
        when (outcome.type) {
            QuestionType.CHOICE -> "→ ${outcome.topOption.text}"
            QuestionType.SCORE -> scoreValue(checkNotNull(outcome.score), outcome.question.options.size)
            QuestionType.NOUL -> "P(true) = ${percent(checkNotNull(outcome.noul))}"
        }
}
