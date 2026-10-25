package org.ggomes.myleo.decision.onnx.domain

/**
 * The three question shapes Julia-1 was trained on. [qtype] is the channel of the graph's
 * `qtype_onehot` input: choice = 0, score = 1, noul (yes/no) = 2.
 */
enum class QuestionType(val wire: String, val qtype: Int) {
    CHOICE("choice", 0),
    SCORE("score", 1),
    NOUL("noul", 2);

    companion object {
        /** Parses the author's `type` string, rejecting anything else. */
        fun fromWire(value: String): QuestionType =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("Unknown question type: $value")
    }
}

/** One scorable option: [id] is the caller's label, [text] is the description scored at a marker. */
data class DecisionOption(val id: String, val text: String) {
    init {
        require(id.isNotBlank()) { "option ids must not be blank" }
        require(text.isNotBlank()) { "option descriptions must not be blank" }
    }
}

/**
 * One named question: what it asks and the 2–20 options to score. `noul` is the model's
 * boolean channel and must be ordered `false`, `true` — the decoder reads `p[1]` as P(true).
 */
data class DecisionQuestion(
    val type: QuestionType,
    val instructions: String,
    val options: List<DecisionOption>,
) {
    init {
        require(instructions.isNotBlank()) { "question instructions must not be blank" }
        require(options.size in 2..20) { "options must contain 2-20 entries" }
        require(options.map { it.id }.toSet().size == options.size) { "option ids must be unique" }
        if (type == QuestionType.NOUL) {
            require(options.size == 2) { "noul needs exactly two options" }
            require(options.map { it.id } == listOf(FALSE_ID, TRUE_ID)) {
                "noul options must be ordered false, true"
            }
        }
    }

    companion object {
        const val FALSE_ID = "false"
        const val TRUE_ID = "true"

        /** Choice question from `id to description` pairs, preserving order. */
        fun choice(instructions: String, options: List<Pair<String, String>>): DecisionQuestion =
            DecisionQuestion(QuestionType.CHOICE, instructions, options.map { DecisionOption(it.first, it.second) })

        /** Ordered rubric; ids are the zero-based index strings the decoder returns. */
        fun score(instructions: String, rubric: List<String>): DecisionQuestion =
            DecisionQuestion(
                QuestionType.SCORE,
                instructions,
                rubric.mapIndexed { index, text -> DecisionOption(index.toString(), text) },
            )

        /** Yes/no question; without descriptions the literal words are the options to score. */
        fun noul(instructions: String, falseText: String = FALSE_ID, trueText: String = TRUE_ID): DecisionQuestion =
            DecisionQuestion(
                QuestionType.NOUL,
                instructions,
                listOf(DecisionOption(FALSE_ID, falseText), DecisionOption(TRUE_ID, trueText)),
            )
    }
}

/** One decision row: the state under consideration plus the question to answer. */
data class DecisionRequest(val state: String, val question: DecisionQuestion) {
    init {
        require(state.isNotBlank()) { "state must not be blank" }
    }
}

/** Compute backend. There is no FP16/NPU option: answers change under fp16 arithmetic. */
enum class Accelerator(val label: String) {
    GPU("GPU FP32"),
    CPU("CPU"),
}

/**
 * Per-stage timings for one decision, in milliseconds. [compileMs] is non-zero only the first
 * time the 1,024-token graph is used (it is compiled on demand), so a cold long request is not
 * silently attributed to the graph call.
 */
data class DecisionTimings(
    val prepareMs: Double,
    val lookupMs: Double,
    val compileMs: Double,
    val writeMs: Double,
    val enqueueMs: Double,
    val readbackMs: Double,
    val decodeMs: Double,
) {
    val graphMs: Double get() = writeMs + enqueueMs + readbackMs

    val totalMs: Double get() = prepareMs + lookupMs + compileMs + graphMs + decodeMs
}

/**
 * One answer. [probabilities] are the temperature-1 softmax over the marker logits and follow
 * the order of `question.options`; `choice` is the winning id, `score` the expected zero-based
 * rubric index and `noul` the probability of true (the author's named-question API).
 */
class DecisionOutcome(
    val question: DecisionQuestion,
    val probabilities: DoubleArray,
    val choiceId: String?,
    val score: Double?,
    val noul: Double?,
    val maxProbability: Double?,
    val argmax: Int,
    val tokenCount: Int,
    val window: Int,
    val accelerator: Accelerator,
    val timings: DecisionTimings,
) {
    init {
        require(probabilities.size == question.options.size) { "one probability per option" }
        require(probabilities.all { it.isFinite() }) { "nonfinite probabilities" }
        require(argmax in question.options.indices) { "argmax out of range" }
    }

    val type: QuestionType get() = question.type

    /** The option with the highest probability (first maximum on ties, like the author's `max`). */
    val topOption: DecisionOption get() = question.options[argmax]
}

/** What actually loaded: which backend, which windows, and how long each phase took. */
data class ModelInfo(
    val accelerator: Accelerator,
    val defaultWindow: Int,
    val availableWindows: List<Int>,
    val tokenizerLoadMs: Double,
    val tableLoadMs: Double,
    val graphLoadMs: Double,
    val totalLoadMs: Double,
) {
    val backend: String get() = accelerator.label
}

/**
 * The decision seam: load once, then one forward pass per request. Deliberately not a
 * `ChatInference` — Julia never generates tokens, so there is nothing to stream or cancel.
 */
interface Decider : AutoCloseable {
    suspend fun load(): ModelInfo
    suspend fun decide(request: DecisionRequest): DecisionOutcome
}
