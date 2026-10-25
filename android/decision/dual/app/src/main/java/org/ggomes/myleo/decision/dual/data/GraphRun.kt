package org.ggomes.myleo.decision.dual.data

/** One graph call: the per-option logits plus the write/enqueue/readback split. */
class GraphRun(
    val tokenLogits: FloatArray,
    val writeMs: Double,
    val enqueueMs: Double,
    val readbackMs: Double,
)
