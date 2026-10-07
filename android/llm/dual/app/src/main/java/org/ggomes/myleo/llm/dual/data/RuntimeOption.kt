package org.ggomes.myleo.llm.dual.data

/** The two on-device runtimes this leaf can drive, in picker order. */
enum class RuntimeOption(val label: String) {
    LITERT_LM("LiteRT-LM"),
    ONNX_GENAI("ONNX GenAI"),
}
