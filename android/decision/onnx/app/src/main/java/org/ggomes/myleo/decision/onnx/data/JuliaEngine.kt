package org.ggomes.myleo.decision.onnx.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ggomes.myleo.decision.onnx.domain.Accelerator
import org.ggomes.myleo.decision.onnx.domain.Decider
import org.ggomes.myleo.decision.onnx.domain.DecisionOutcome
import org.ggomes.myleo.decision.onnx.domain.DecisionRequest
import org.ggomes.myleo.decision.onnx.domain.DecisionTimings
import org.ggomes.myleo.decision.onnx.domain.ModelInfo

/**
 * [Decider] over ONNX Runtime: the checkpoint's BPE tokenizer + the Julia-1 decision graph. The
 * graph owns the embedding table (577 MB of external weights), so there is no host-side lookup.
 *
 * One static 1,024-token budget, encoded with the same strict marker sequence as the LiteRT leaf
 * (`headLength = 512`), so an accepted request produces the same token ids on both runtimes.
 * CPU only: see [OnnxGraphRunner] and the README.
 */
class JuliaEngine(
    private val files: JuliaModelFiles,
    private val makeRunner: (model: File, window: Int) -> JuliaIdGraphRunner,
) : Decider {

    private var tokenizer: JuliaTokenizer? = null
    private var runner: JuliaIdGraphRunner? = null
    private var cachedInfo: ModelInfo? = null
    private var closed = false

    override suspend fun load(): ModelInfo = withContext(Dispatchers.Default) {
        check(!closed) { "JuliaEngine is closed" }
        cachedInfo?.let { return@withContext it }

        val totalStart = now()
        val createdTokenizer = JuliaTokenizer(files.tokenizer)
        val tokenizerDone = now()
        val createdRunner = makeRunner(files.model, WINDOW)
        val graphDone = now()

        tokenizer = createdTokenizer
        runner = createdRunner
        return@withContext ModelInfo(
            accelerator = Accelerator.CPU,
            defaultWindow = WINDOW,
            availableWindows = listOf(WINDOW),
            tokenizerLoadMs = tokenizerDone - totalStart,
            tableLoadMs = 0.0,
            graphLoadMs = graphDone - tokenizerDone,
            totalLoadMs = graphDone - totalStart,
        ).also { cachedInfo = it }
    }

    override suspend fun decide(request: DecisionRequest): DecisionOutcome = withContext(Dispatchers.Default) {
        check(!closed) { "JuliaEngine is closed" }
        val tokenizer = checkNotNull(tokenizer) { "load() must succeed before decide()" }
        val runner = checkNotNull(runner) { "load() must succeed before decide()" }

        val prepareStart = now()
        val sequence =
            JuliaSequenceBuilder(tokenizer, window = WINDOW, headLength = HEAD_LENGTH)
                .build(request.state, request.question)
        val prepareMs = now() - prepareStart

        val run = runner.run(JuliaInputs.pack(sequence))

        val decodeStart = now()
        val decoded =
            JuliaDecoder.decode(
                JuliaDecoder.gather(run.tokenLogits, sequence.markers),
                request.question,
            )
        val decodeMs = now() - decodeStart

        return@withContext DecisionOutcome(
            question = request.question,
            probabilities = decoded.probabilities,
            choiceId = decoded.choiceId,
            score = decoded.score,
            noul = decoded.noul,
            maxProbability = decoded.maxProbability,
            argmax = decoded.argmax,
            tokenCount = sequence.tokenCount,
            window = WINDOW,
            accelerator = Accelerator.CPU,
            timings =
                DecisionTimings(
                    prepareMs = prepareMs,
                    lookupMs = 0.0,
                    compileMs = 0.0,
                    writeMs = run.writeMs,
                    enqueueMs = run.enqueueMs,
                    readbackMs = run.readbackMs,
                    decodeMs = decodeMs,
                ),
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { runner?.close() }
        runner = null
        tokenizer = null
        cachedInfo = null
    }

    private fun now(): Double = System.nanoTime() / 1_000_000.0

    companion object {
        /** Token budget of the export (the author's browser host also defaults to 1,024). */
        const val WINDOW = 1024

        /** Question + options budget; matches the LiteRT leaf's 1,024-token encoding. */
        const val HEAD_LENGTH = 512
    }
}
