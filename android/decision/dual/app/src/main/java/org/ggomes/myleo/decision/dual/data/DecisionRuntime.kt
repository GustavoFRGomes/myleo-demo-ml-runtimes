package org.ggomes.myleo.decision.dual.data

/** The two runtimes this leaf can drive, in picker order. */
enum class DecisionRuntime(
    val label: String,
    val downloadLabel: String,
    val backendHint: String,
) {
    LITERT(
        label = "LiteRT",
        downloadLabel = "Download LiteRT (~604 MB)",
        backendHint = "GPU FP32 first, CPU fallback · windows 512/1024",
    ),
    ONNX(
        label = "ONNX",
        downloadLabel = "Download ONNX (~614 MB)",
        backendHint = "CPU · window 1024",
    ),
}
