package org.ggomes.myleo.vectorsearch.dual.data

import org.tensorflow.lite.Interpreter

/**
 * [InferenceRunner] over a raw LiteRT [Interpreter] (the `litert` AAR's
 * `org.tensorflow.lite` binding over `libLiteRt.so`).
 *
 * Assumes a BERT-style sentence model (e.g. MiniLM-L6-v2) with INT32
 * `ids`/`mask`/`segment_ids` (`[1 x seqLen]`) inputs — located by tensor
 * *name*, since order varies per file — and a float `[1 x seqLen x dim]`
 * sequence output, mean-pooled here. Confirm the layout of the bundled model
 * with Model Explorer before changing these assumptions.
 */
class InterpreterRunner(private val interpreter: Interpreter) : InferenceRunner {

    private var inputOrder: InputOrder? = null

    override fun runEmbedding(ids: IntArray, mask: IntArray, segmentIds: IntArray): FloatArray {
        val order = inputOrder ?: run {
            val names = (0 until interpreter.inputTensorCount).map { interpreter.getInputTensor(it).name() }
            resolveInputOrder(names).also { inputOrder = it }
        }
        require(interpreter.inputTensorCount == 3) {
            "expected 3 inputs (ids, mask, segment_ids), got ${interpreter.inputTensorCount}"
        }
        val inputs: Array<Any> = Array(3) { i ->
            when (i) {
                order.ids -> arrayOf(ids)
                order.mask -> arrayOf(mask)
                order.segment -> arrayOf(segmentIds)
                else -> error("unmapped input at index $i")
            }
        }
        val shape = interpreter.getOutputTensor(0).shape()
        require(shape.size == 3) {
            "unexpected model output rank ${shape.size}, expected [1 x seqLen x dim]"
        }
        val (seqLen, dim) = shape[1] to shape[2]
        val output = Array(1) { Array(seqLen) { FloatArray(dim) } }
        interpreter.runForMultipleInputsOutputs(inputs, mutableMapOf<Int, Any>(0 to output))
        val hidden = FloatArray(seqLen * dim) { i -> output[0][i / dim][i % dim] }
        return meanPool(hidden, seqLen, dim, mask)
    }

    override fun close() = interpreter.close()
}

/** Resolved (ids, mask, segment) input indices. */
internal data class InputOrder(val ids: Int, val mask: Int, val segment: Int)

/**
 * Maps tensor names to (ids, mask, segment) input indices. Pure — unit-tested.
 */
internal fun resolveInputOrder(names: List<String>): InputOrder {
    require(names.size >= 3) {
        "embedding model has ${names.size} inputs, expected at least 3 (ids, mask, segment_ids)"
    }
    var ids: Int? = null
    var mask: Int? = null
    var segment: Int? = null
    for ((i, name) in names.withIndex()) {
        val lower = name.lowercase()
        when {
            "mask" in lower -> mask = i
            "segment" in lower || "type" in lower -> segment = i
            "ids" in lower || "id" in lower -> ids = i
        }
    }
    require(ids != null && mask != null && segment != null) {
        "cannot map model inputs by name: ${names.joinToString(", ")}"
    }
    return InputOrder(ids, mask, segment)
}
