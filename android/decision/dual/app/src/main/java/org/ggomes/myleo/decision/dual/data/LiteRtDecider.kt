package org.ggomes.myleo.decision.dual.data

import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ggomes.myleo.decision.dual.domain.Accelerator
import org.ggomes.myleo.decision.dual.domain.Decider
import org.ggomes.myleo.decision.dual.domain.DecisionOutcome
import org.ggomes.myleo.decision.dual.domain.DecisionRequest
import org.ggomes.myleo.decision.dual.domain.DecisionTimings
import org.ggomes.myleo.decision.dual.domain.ModelInfo

/**
 * LiteRT lane of the picker: tokenizer + memory-mapped token table + one compiled graph, all
 * driven on [JuliaLiteRt]'s single native thread.
 *
 * GPU FP32 is tried first and CPU is the fallback; the backend that actually compiled is reported
 * in [ModelInfo] and every outcome. The 512-token graph compiles during [load]; a request that
 * does not fit uses the 1,024 graph when installed, compiled on first use.
 */
class LiteRtDecider(
    private val files: LiteRtFiles,
    private val makeGraph: (accelerator: Accelerator, window: Int, graph: File) -> JuliaGraphRunner,
    private val accelerators: List<Accelerator> = listOf(Accelerator.GPU, Accelerator.CPU),
    private val runtime: JuliaRuntime = JuliaLiteRt,
) : Decider {

    private var tokenizer: JuliaTokenizer? = null
    private var table: JuliaTokenTable? = null
    private val graphs = linkedMapOf<Int, JuliaGraphRunner>()
    private var activeAccelerator: Accelerator? = null
    private var cachedInfo: ModelInfo? = null
    private var closed = false

    override suspend fun load(): ModelInfo = withContext(Dispatchers.Default) {
        runtime.call { loadBlocking() }
    }

    private fun loadBlocking(): ModelInfo {
        check(!closed) { "LiteRtDecider is closed" }
        cachedInfo?.let { return it }

        val totalStart = now()
        val createdTokenizer = JuliaTokenizer(files.tokenizer)
        val tokenizerDone = now()
        val createdTable = JuliaTokenTable(files.tokenTable)
        val tableDone = now()
        val defaultGraph =
            files.graph(DEFAULT_WINDOW)
                ?: throw IllegalStateException(
                    "missing ${LiteRtModelLocator.graphFileName(DEFAULT_WINDOW)} in ${files.directory}",
                )

        val (runner, accelerator) =
            try {
                compileFirst(defaultGraph, DEFAULT_WINDOW)
            } catch (e: Exception) {
                runCatching { createdTable.close() }
                throw e
            }
        val graphDone = now()

        tokenizer = createdTokenizer
        table = createdTable
        graphs[DEFAULT_WINDOW] = runner
        activeAccelerator = accelerator

        return ModelInfo(
            accelerator = accelerator,
            defaultWindow = DEFAULT_WINDOW,
            availableWindows = files.windows,
            tokenizerLoadMs = tokenizerDone - totalStart,
            tableLoadMs = tableDone - tokenizerDone,
            graphLoadMs = graphDone - tableDone,
            totalLoadMs = graphDone - totalStart,
        ).also { cachedInfo = it }
    }

    override suspend fun decide(request: DecisionRequest): DecisionOutcome = withContext(Dispatchers.Default) {
        runtime.call { decideBlocking(request) }
    }

    private fun decideBlocking(request: DecisionRequest): DecisionOutcome {
        check(!closed) { "LiteRtDecider is closed" }
        val tokenizer = checkNotNull(tokenizer) { "load() must succeed before decide()" }
        val table = checkNotNull(table) { "load() must succeed before decide()" }
        val accelerator = checkNotNull(activeAccelerator) { "load() must succeed before decide()" }

        var compileMs = 0.0
        val prepareStart = now()

        var sequence: JuliaSequence? = null
        var window = 0
        var failure: EncodingException? = null
        for (candidate in files.windows) {
            try {
                sequence = JuliaSequenceBuilder(tokenizer, candidate).build(request.state, request.question)
                window = candidate
                break
            } catch (e: EncodingException) {
                failure = e
            }
        }
        val built = sequence ?: throw (failure ?: EncodingException("No Julia graph is installed"))
        val graph = graphFor(window, accelerator) { compileMs += it }
        val usedAccelerator = graph.accelerator
        val prepareMs = (now() - prepareStart - compileMs).coerceAtLeast(0.0)

        val lookupStart = now()
        val embeddings = table.gather(built.ids, window)
        val lookupMs = now() - lookupStart

        val attentionMask = FloatArray(window) { if (it < built.ids.size) 1f else 0f }
        val qtypeOnehot = FloatArray(3).also { it[built.qtype] = 1f }
        val run = graph.run(embeddings, attentionMask, qtypeOnehot)

        val decodeStart = now()
        val decoded = JuliaDecoder.decode(JuliaDecoder.gather(run.tokenLogits, built.markers), request.question)
        val decodeMs = now() - decodeStart

        return DecisionOutcome(
            question = request.question,
            probabilities = decoded.probabilities,
            choiceId = decoded.choiceId,
            score = decoded.score,
            noul = decoded.noul,
            maxProbability = decoded.maxProbability,
            argmax = decoded.argmax,
            tokenCount = built.tokenCount,
            window = window,
            accelerator = usedAccelerator,
            timings =
                DecisionTimings(
                    prepareMs = prepareMs,
                    lookupMs = lookupMs,
                    compileMs = compileMs,
                    writeMs = run.writeMs,
                    enqueueMs = run.enqueueMs,
                    readbackMs = run.readbackMs,
                    decodeMs = decodeMs,
                ),
        )
    }

    private fun graphFor(
        window: Int,
        accelerator: Accelerator,
        onCompile: (Double) -> Unit,
    ): JuliaGraphRunner =
        graphs[window] ?: run {
            val file = files.graph(window)
                ?: throw IllegalStateException("missing graph for window $window")
            val start = now()
            val (runner, _) = compileFirst(file, window)
            onCompile(now() - start)
            graphs[window] = runner
            runner
        }

    /** Tries [accelerators] in order and returns the first graph that compiles. */
    private fun compileFirst(graph: File, window: Int): Pair<JuliaGraphRunner, Accelerator> {
        var last: Exception? = null
        for (accelerator in accelerators) {
            try {
                return makeGraph(accelerator, window, graph) to accelerator
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "Julia-1 LiteRT ${accelerator.label} graph failed to compile; trying next", e)
            }
        }
        throw IllegalStateException(
            "Julia-1 graph ${graph.name} could not be compiled on ${accelerators.joinToString { it.label }}",
            last,
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        runtime.submit {
            graphs.values.forEach { runCatching { it.close() } }
            graphs.clear()
            runCatching { table?.close() }
            table = null
            tokenizer = null
            activeAccelerator = null
            cachedInfo = null
        }
    }

    private fun now(): Double = System.nanoTime() / 1_000_000.0

    companion object {
        private const val TAG = "MyLeo-Julia"

        /** Window of the graph compiled at load; the 1,024 graph is compiled on first use. */
        const val DEFAULT_WINDOW = 512
    }
}
