package org.ggomes.myleo.decision.dual.data

import org.ggomes.myleo.decision.dual.domain.DecisionQuestion

/** The request does not fit the window without truncation; strict encoding rejects it. */
class EncodingException(message: String) : IllegalArgumentException(message)

/** Unpadded token ids plus one marker position per option, for one graph invocation. */
class JuliaSequence(val ids: IntArray, val markers: IntArray, val qtype: Int) {
    val tokenCount: Int get() = ids.size
}

/**
 * Builds one request row exactly like the author's strict encoding:
 *
 * ```
 * <bos> "{type} question: {instructions}" <eos>
 *   [ <mask> option-text ] * N
 *   <eos> state <eos>
 * ```
 *
 * The logit at each `<mask>` scores the option text that follows it. Nothing is truncated: a
 * request that does not fit the window raises [EncodingException] instead of silently losing
 * part of the question, an option or the state.
 *
 * Ported from `Julia-1-LiteRT`'s `JuliaSequenceBuilder` (Apache-2.0), with the same constants,
 * budgets and error conditions; the question model lives in this repo's domain package.
 */
class JuliaSequenceBuilder(
    private val encoder: JuliaEncoder,
    val window: Int = 512,
    /** Question + options budget; any value below `window - 4` keeps the author's strict rules. */
    val headLength: Int = minOf(512, window - 5),
) {
    init {
        require(window > 0 && headLength in 1 until window - 4) { "Invalid window or head budget" }
    }

    fun build(state: String, question: DecisionQuestion): JuliaSequence {
        require(
            (listOf(state, question.instructions) + question.options.map { it.text }).none {
                it.contains(MASK_LITERAL)
            },
        ) {
            "Reserved model marker in request"
        }
        val head = encoder.encode("${question.type.wire} question: ${question.instructions}")
        val optionIds = question.options.map { encoder.encode(" ${it.text}") }
        if (optionIds.any { it.size > MAX_OPTION_TOKENS }) {
            throw EncodingException("Option exceeds the $MAX_OPTION_TOKENS-token model contract")
        }
        val budget = headLength - optionIds.sumOf { it.size + 1 }
        if (budget < 16) {
            // The author's squeeze would cut options here; strict encoding rejects instead.
            val perOption = maxOf(4, Math.floorDiv(headLength - 16, optionIds.size))
            if (optionIds.any { it.size + 1 > perOption }) {
                throw EncodingException("Options exceed the head budget")
            }
        }
        if (head.size > budget) {
            throw EncodingException("Question and options exceed the head budget")
        }

        val ids = ArrayList<Int>(window)
        ids.add(CLS)
        head.forEach { ids.add(it) }
        ids.add(SEP)
        val markers = IntArray(optionIds.size)
        optionIds.forEachIndexed { index, option ->
            markers[index] = ids.size
            ids.add(MASK)
            option.forEach { ids.add(it) }
        }
        ids.add(SEP)

        val stateIds = encoder.encode(state)
        val needed = ids.size + stateIds.size + 1
        if (needed > window) {
            throw EncodingException("Request needs $needed tokens; the window is $window")
        }
        stateIds.forEach { ids.add(it) }
        ids.add(SEP)

        return JuliaSequence(ids.toIntArray(), markers, question.type.qtype)
    }

    companion object {
        /** `<pad>` of the checkpoint tokenizer; padding rows gather this real embedding. */
        const val PAD = 0

        /** `<eos>` separates the question head, the options and the state. */
        const val SEP = 1

        /** `<bos>` starts every sequence. */
        const val CLS = 2

        /** `<mask>` marks the position whose logit scores the option that follows it. */
        const val MASK = 4

        /** Tokens allowed per option after its marker. */
        const val MAX_OPTION_TOKENS = 48

        private const val MASK_LITERAL = "<mask>"
    }
}
