package org.ggomes.myleo.decision.dual.data

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Serializes every native LiteRT call onto one thread. `CompiledModel` GPU work expects a
 * stable thread/environment, and the official Julia sample does the same. An interface so the
 * engine can be exercised without the runtime if needed.
 */
interface JuliaRuntime {
    fun <T> call(block: () -> T): T

    /** Fire-and-forget variant for release paths that must not block the caller. */
    fun submit(block: () -> Unit)
}

/** The process-wide LiteRT runtime: one daemon thread, one [com.google.ai.edge.litert.Environment]. */
object JuliaLiteRt : JuliaRuntime {
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "Julia-LiteRT").apply { isDaemon = true }
        }

    @Volatile private var sharedEnvironment: com.google.ai.edge.litert.Environment? = null

    override fun <T> call(block: () -> T): T =
        try {
            executor.submit(Callable { block() }).get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }

    override fun submit(block: () -> Unit) {
        executor.execute(block)
    }

    /** Environment options mirror the official sample; harmless for CPU and required for GPU. */
    internal fun environment(context: android.content.Context): com.google.ai.edge.litert.Environment =
        sharedEnvironment
            ?: com.google.ai.edge.litert.Environment.create(
                context,
                mapOf(
                    com.google.ai.edge.litert.Environment.Option.DispatchLibraryDir to
                        context.applicationInfo.nativeLibraryDir,
                    com.google.ai.edge.litert.Environment.Option.CompilerPluginLibraryDir to
                        context.applicationInfo.nativeLibraryDir,
                ),
            ).also { sharedEnvironment = it }
}
