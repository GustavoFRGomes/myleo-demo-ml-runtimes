package org.ggomes.myleo.decision.onnx.data

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.LongBuffer

/** One graph call: the per-option logits plus the write/enqueue/readback split. */
class GraphRun(
    val tokenLogits: FloatArray,
    val writeMs: Double,
    val enqueueMs: Double,
    val readbackMs: Double,
)

/**
 * The inference seam. ONNX Runtime takes ids (its graph owns the embedding table), unlike the
 * LiteRT export which takes gathered embeddings. A fake is enough to test the engine's
 * sequencing and timings without a 577 MB weights file.
 */
interface JuliaIdGraphRunner : AutoCloseable {
    /** Static token budget the graph was exported for; informational. */
    val window: Int

    fun run(inputs: JuliaInputs.Packed): GraphRun
}

/**
 * [JuliaIdGraphRunner] over `SupersonicLabs/Julia-1-ONNX` and ONNX Runtime for Android.
 *
 * The export is the author's decision graph with the token table baked in; inputs are int64
 * `input_ids`/`attention_mask`/`marker_pos`/`qtype` and a bool `marker_mask`, output is float
 * `logits` with one value per marker. CPU only: the published export targets WebGPU in a
 * browser, and no accelerated Android EP for this graph has been validated (see README).
 */
class OnnxGraphRunner private constructor(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    override val window: Int,
) : JuliaIdGraphRunner {

    override fun run(inputs: JuliaInputs.Packed): GraphRun {
        val writeStart = System.nanoTime()
        val tensors =
            listOf(
                OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(inputs.inputIds),
                    longArrayOf(1, inputs.length.toLong()),
                ),
                OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(inputs.attentionMask),
                    longArrayOf(1, inputs.length.toLong()),
                ),
                OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(inputs.markerPositions),
                    longArrayOf(1, inputs.markerCount.toLong()),
                ),
                OnnxTensor.createTensor(
                    environment,
                    ByteBuffer.wrap(inputs.markerMask),
                    longArrayOf(1, inputs.markerCount.toLong()),
                    OnnxJavaType.BOOL,
                ),
                OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(longArrayOf(inputs.qtype)),
                    longArrayOf(1),
                ),
            )
        val feeds =
            linkedMapOf(
                "input_ids" to tensors[0],
                "attention_mask" to tensors[1],
                "marker_pos" to tensors[2],
                "marker_mask" to tensors[3],
                "qtype" to tensors[4],
            )
        val written = System.nanoTime()

        var result: OrtSession.Result? = null
        try {
            result = session.run(feeds)
            val enqueued = System.nanoTime()
            val logitsTensor =
                (result.get(OUTPUT_NAME).orElseThrow { IllegalStateException("missing $OUTPUT_NAME") } as OnnxTensor)
            val logits = FloatArray(inputs.markerCount)
            logitsTensor.floatBuffer.get(logits)
            val read = System.nanoTime()
            require(logits.size == inputs.markerCount) { "unexpected $OUTPUT_NAME size ${logits.size}" }
            return GraphRun(
                tokenLogits = logits,
                writeMs = millis(written - writeStart),
                enqueueMs = millis(enqueued - written),
                readbackMs = millis(read - enqueued),
            )
        } finally {
            tensors.forEach { runCatching { it.close() } }
            runCatching { result?.close() }
        }
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        const val OUTPUT_NAME = "logits"

        /** Inputs the export declares; validated at load so a wrong package fails loudly. */
        val INPUT_NAMES =
            listOf("input_ids", "attention_mask", "marker_pos", "marker_mask", "qtype")

        fun create(model: File, window: Int, intraOpThreads: Int = 4): OnnxGraphRunner {
            val environment = OrtEnvironment.getEnvironment()
            val options =
                OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(intraOpThreads)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
            try {
                val session = environment.createSession(model.absolutePath, options)
                try {
                    val names = session.getInputInfo().keys
                    val missing = INPUT_NAMES.filterNot { it in names }
                    check(missing.isEmpty()) {
                        "Julia-1 ONNX graph is missing inputs $missing (found $names)"
                    }
                    return OnnxGraphRunner(environment, session, window)
                } catch (failure: Throwable) {
                    runCatching { session.close() }
                    throw failure
                }
            } finally {
                runCatching { options.close() }
            }
        }

        private fun millis(nanos: Long): Double = nanos / 1_000_000.0
    }
}
