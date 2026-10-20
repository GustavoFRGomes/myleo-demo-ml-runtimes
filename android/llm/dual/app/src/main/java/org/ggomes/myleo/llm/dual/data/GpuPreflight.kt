package org.ggomes.myleo.llm.dual.data

/**
 * Outcome of the last LiteRT-LM **GPU** load attempt, remembered across app runs.
 *
 * [PENDING] is written *before* `Engine.initialize()` and replaced the moment that
 * call returns (either way). Finding it still on record when a later run starts
 * therefore means the previous run died inside `initialize()` — an OS low-memory
 * kill, which arrives as a `SIGKILL` and can never be seen by an in-process `try/catch`.
 */
enum class GpuVerdict {
    /** Never attempted (fresh install). */
    UNKNOWN,

    /** An attempt is in flight. A leftover value means the run was killed mid-load. */
    PENDING,

    /** GPU initialized at least once — keep using it. */
    OK,

    /** GPU failed: either a caught init error or a killed attempt. */
    BAD,
}

/**
 * A leftover [GpuVerdict.PENDING] means the attempt never reached success or failure,
 * i.e. the process was killed mid-load — so it is treated as [GpuVerdict.BAD].
 */
fun GpuVerdict.resolved(): GpuVerdict =
    if (this == GpuVerdict.PENDING) GpuVerdict.BAD else this

/**
 * Pre-flight check that answers "do we already know a GPU load can't work here?"
 * without paying for the attempt.
 *
 * The GPU backend holds the model **and** a private copy of its weights in GPU memory.
 * That copy is not file-backed, so unlike the CPU path's `mmap`ed pages it cannot be
 * reclaimed under memory pressure. The 2× + headroom shape was calibrated on Phi-4-mini
 * q8 (~3.9 GB), where the process needed ~6.1 GB of graphics memory (`GL mtrack`) — see
 * `docs/benchmarks.md`. The formula is model-size based, so the shipped MiniCPM5-1B
 * (~793 MB) lands far below the gate and still attempts GPU.
 *
 * The gate compares against **total** physical RAM, not free RAM: a device whose RAM is
 * smaller than the required footprint can never hold it, whatever else is running,
 * whereas the free RAM at one instant is a poor predictor (a 12 GB device succeeded
 * with only 0.66 GB free at the peak).
 *
 * SDK-free and pure so the decision is unit-tested without a model or a device.
 */
object GpuPreflight {
    /** Resident model + the GPU's private weight copy, ~1× the model file each. */
    const val MEMORY_FACTOR = 2.0

    /** RAM the OS + app framework need on top of the model. */
    const val OS_HEADROOM_BYTES = 1_610_612_736L // 1.5 GiB

    /** Total RAM a GPU load needs to hold a model of [modelBytes]. */
    fun requiredBytes(modelBytes: Long): Long =
        (modelBytes * MEMORY_FACTOR).toLong() + OS_HEADROOM_BYTES

    /** `true` when a GPU attempt is worth making here and it hasn't already failed. */
    fun canAttemptGpu(modelBytes: Long, totalRamBytes: Long, verdict: GpuVerdict): Boolean =
        verdict.resolved() != GpuVerdict.BAD && totalRamBytes >= requiredBytes(modelBytes)
}
