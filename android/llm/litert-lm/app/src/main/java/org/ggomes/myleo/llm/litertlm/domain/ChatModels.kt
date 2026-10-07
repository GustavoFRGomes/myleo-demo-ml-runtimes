package org.ggomes.myleo.llm.litertlm.domain

import java.util.UUID

/** Role of a message in the chat thread. */
enum class ChatRole { SYSTEM, USER, ASSISTANT }

/** One entry in the chat thread. `isStreaming` marks the in-progress assistant reply. */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val isStreaming: Boolean = false,
    val metrics: GenerationMetrics? = null,
)

/** Metadata shown once the runtime has loaded a model. */
data class ChatModelInfo(
    val displayName: String,
    val backend: String,
    val loadMs: Long,
    val sizeBytes: Long,
    val sourceUrl: String?,
)

/** Per-generation performance report: TTFT + tokens/sec. */
data class GenerationMetrics(
    val ttftMs: Long,
    val totalMs: Long,
    val generatedTokens: Int,
) {
    val tokensPerSecond: Double
        get() {
            val decodeMs = totalMs - ttftMs
            return if (decodeMs > 0 && generatedTokens > 0) {
                generatedTokens / (decodeMs / 1000.0)
            } else {
                0.0
            }
        }
}

/** Sampling parameters, validated before being mapped onto the runtime sampler. */
data class GenerationParams(
    val temperature: Double = 0.8,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val maxTokens: Int = 512,
) {
    fun validate() {
        require(temperature >= 0) { "temperature must be >= 0" }
        require(topK >= 1) { "top-k must be >= 1" }
        require(topP in 0.0..1.0) { "top-p must be between 0 and 1" }
        require(maxTokens >= 1) { "max tokens must be >= 1" }
    }

    /** Clamps every field into range so the sliders can never produce an invalid config. */
    fun clamped(): GenerationParams = GenerationParams(
        temperature = temperature.coerceIn(0.0, 2.0),
        topK = topK.coerceIn(1, 100),
        topP = topP.coerceIn(0.05, 1.0),
        maxTokens = maxTokens.coerceIn(1, 4096),
    )

    companion object {
        val Default = GenerationParams()
    }
}
