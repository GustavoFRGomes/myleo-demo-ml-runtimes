package org.ggomes.myleo.vectorsearch.dual.data

import android.util.Log
import org.ggomes.myleo.vectorsearch.dual.domain.CalculateEmbeddingUseCase
import org.ggomes.myleo.vectorsearch.dual.domain.Embedder
import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime
import kotlin.system.measureTimeMillis

/** A loaded runtime: usable embedder plus its load report for the stats sheet. */
data class LoadedRuntime(
    val embedder: Embedder,
    val modelName: String,
    val modelLoadMs: Long,
)

/**
 * Owns the active [InferenceRuntime] and lazily loads each runtime's model on
 * first selection (never at startup). Loaded runtimes stay cached for the
 * session so re-selecting is instant.
 *
 * The index is shared between runtimes and holds one runtime's vectors at a
 * time: selecting a different runtime clears it first. Both runtimes embed the
 * same 384-d MiniLM weights, so the spaces are comparable — the clear exists
 * because a corpus ingested through one runtime has no vectors from the other,
 * and stale hits would mix models if the assets ever drift apart.
 *
 * The [load] / [hasModelAssets] seams are injected so unit tests never touch
 * assets or native runtimes.
 */
class RuntimeManager(
    val documents: DocumentRepository,
    private val load: (InferenceRuntime) -> LoadedRuntime,
    private val hasModelAssets: (InferenceRuntime) -> Boolean = { true },
) {
    private val loaded = mutableMapOf<InferenceRuntime, LoadedRuntime>()

    /** Active runtime, null until the first selection. */
    var current: InferenceRuntime? = null
        private set

    fun isLoaded(runtime: InferenceRuntime): Boolean = synchronized(this) {
        loaded.containsKey(runtime)
    }

    fun hasBundledModel(runtime: InferenceRuntime): Boolean = hasModelAssets(runtime)

    fun currentLoaded(): LoadedRuntime? = synchronized(this) {
        current?.let { loaded[it] }
    }

    /** Use case over the active runtime. Fails when no runtime is selected yet. */
    fun useCase(): Result<CalculateEmbeddingUseCase> = synchronized(this) {
        val runtime = current ?: return Result.failure(IllegalStateException("no runtime selected"))
        val embedder = loaded[runtime]?.embedder
            ?: return Result.failure(IllegalStateException("runtime $runtime not loaded"))
        Result.success(CalculateEmbeddingUseCase(DefaultEmbeddingRepository(embedder)))
    }

    /**
     * Selects [runtime], loading its model on first use. Switching to a
     * different runtime clears the shared index first. Failures (e.g. missing
     * model assets) leave the previous selection untouched.
     */
    fun select(runtime: InferenceRuntime): Result<LoadedRuntime> = synchronized(this) {
        loaded[runtime]?.let { cached ->
            if (current != runtime) {
                documents.clear()
                Log.d(TAG, "switched to ${runtime.label}, index cleared")
                current = runtime
            }
            return Result.success(cached)
        }
        val fresh = try {
            var created: LoadedRuntime? = null
            val ms = measureTimeMillis { created = load(runtime) }
            val it = created ?: return Result.failure(IllegalStateException("loader returned null"))
            it.copy(modelLoadMs = ms)
        } catch (e: Exception) {
            Log.w(TAG, "${runtime.label} load failed: ${e.message}")
            return Result.failure(e)
        }
        if (current != null && current != runtime) {
            documents.clear()
            Log.d(TAG, "switched to ${runtime.label}, index cleared")
        }
        loaded[runtime] = fresh
        current = runtime
        Log.d(TAG, "${runtime.label} model loaded in ${fresh.modelLoadMs}ms")
        Result.success(fresh)
    }

    companion object {
        private const val TAG = "MyLeo-Runtime"
    }
}
