package org.ggomes.myleo.decision.dual.data

import kotlin.math.exp
import org.ggomes.myleo.decision.dual.domain.DecisionQuestion
import org.ggomes.myleo.decision.dual.domain.QuestionType

/** The author's answer arithmetic turned the marker logits into these fields. */
class DecodedDecision(
    val probabilities: DoubleArray,
    val argmax: Int,
    val choiceId: String?,
    val score: Double?,
    val noul: Double?,
    val maxProbability: Double?,
)

/**
 * Port of the Julia-1 named-question arithmetic (`julia/typed.py`): float64 softmax at
 * temperature 1, no calibration. Deliberately free of SDK/Android types so the exact answer
 * rules are unit-tested on the JVM.
 */
object JuliaDecoder {

    /** Reads the option logits at the sequence builder's marker positions. */
    fun gather(tokenLogits: FloatArray, markers: IntArray): DoubleArray {
        require(tokenLogits.isNotEmpty()) { "token_logits is empty" }
        return DoubleArray(markers.size) { index ->
            val marker = markers[index]
            require(marker in tokenLogits.indices) { "marker $marker outside token_logits" }
            tokenLogits[marker].toDouble()
        }
    }

    /** `p_i = exp(z_i - max) / sum`, computed in float64 with a sequential sum, like typed.py. */
    fun softmax(logits: DoubleArray): DoubleArray {
        require(logits.isNotEmpty()) { "At least one logit is required" }
        require(logits.all { it.isFinite() }) { "Nonfinite model scores" }
        val maximum = logits.max()
        val exponents = DoubleArray(logits.size) { exp(logits[it] - maximum) }
        var total = 0.0
        for (value in exponents) {
            total += value
        }
        return DoubleArray(logits.size) { exponents[it] / total }
    }

    /**
     * choice = winning option id, score = expected zero-based rubric index (not rounded),
     * noul = P(true) — the second option, whose id the domain type pins to `true`.
     */
    fun decode(markerLogits: DoubleArray, question: DecisionQuestion): DecodedDecision {
        require(markerLogits.size == question.options.size) { "Logits/option count mismatch" }
        val probabilities = softmax(markerLogits)
        var argmax = 0
        for (index in 1 until probabilities.size) {
            if (probabilities[index] > probabilities[argmax]) {
                argmax = index
            }
        }
        return when (question.type) {
            QuestionType.CHOICE ->
                DecodedDecision(
                    probabilities = probabilities,
                    argmax = argmax,
                    choiceId = question.options[argmax].id,
                    score = null,
                    noul = null,
                    maxProbability = probabilities.max(),
                )
            QuestionType.SCORE -> {
                var expectation = 0.0
                for (index in probabilities.indices) {
                    expectation += index * probabilities[index]
                }
                DecodedDecision(
                    probabilities = probabilities,
                    argmax = argmax,
                    choiceId = null,
                    score = expectation,
                    noul = null,
                    maxProbability = probabilities.max(),
                )
            }
            QuestionType.NOUL ->
                DecodedDecision(
                    probabilities = probabilities,
                    argmax = argmax,
                    choiceId = null,
                    score = null,
                    noul = probabilities[1],
                    maxProbability = null,
                )
        }
    }
}
