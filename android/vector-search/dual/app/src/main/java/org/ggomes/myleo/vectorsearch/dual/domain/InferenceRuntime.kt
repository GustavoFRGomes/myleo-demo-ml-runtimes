package org.ggomes.myleo.vectorsearch.dual.domain

/**
 * The embedding runtime backing the shared index. Only one is active at a
 * time; switching clears the index. Both runtimes embed the same MiniLM-L6-v2
 * weights (`.tflite` / `.onnx`) into 384-d vectors, so the shared index stays
 * comparable — clearing only avoids mixing sessions, not spaces.
 */
enum class InferenceRuntime {
    LITERT,
    ONNX;

    /** Short label for the picker and the search-screen switcher. */
    val label: String
        get() = when (this) {
            LITERT -> "LiteRT"
            ONNX -> "ONNX"
        }

    /** Model file name shown in the picker and the stats sheet. */
    val modelFile: String
        get() = when (this) {
            LITERT -> "embedding.tflite"
            ONNX -> "embedding.onnx"
        }

    /** Native embedding width (identical for both — same MiniLM weights). */
    val dimensions: Int = 384

    /** One-line model description for the runtime picker. */
    val description: String
        get() = when (this) {
            LITERT -> "MiniLM-L6-v2 as TFLite (384-d, ~90 MB)"
            ONNX -> "MiniLM-L6-v2 via ONNX Runtime (384-d, ~90 MB)"
        }

    companion object {
        /** Width of the shared ObjectBox vector field. */
        const val INDEX_DIMENSIONS = 384
    }
}
