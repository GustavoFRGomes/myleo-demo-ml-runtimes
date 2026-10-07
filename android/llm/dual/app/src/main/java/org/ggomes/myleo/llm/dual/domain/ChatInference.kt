package org.ggomes.myleo.llm.dual.domain

import kotlinx.coroutines.flow.Flow

/**
 * Narrow inference-engine boundary for chat generation. Implementations wrap a
 * runtime SDK (here LiteRT-LM) behind this interface so the ViewModel never
 * touches the SDK directly, and unit tests can drive a scripted fake.
 */
interface ChatInference {
    /** Loads the model from its resolved path. Idempotent. */
    suspend fun loadModel(): ChatModelInfo

    /** Streams an assistant reply for the given system prompt + history (last message = prompt). */
    fun generate(systemPrompt: String, messages: List<ChatMessage>, params: GenerationParams): Flow<String>

    /** Cancels the active generation, if any. */
    suspend fun cancel()

    /** Current KV-cache token count (used to derive generated-token counts). */
    suspend fun tokenCount(): Int

    /** Releases any loaded model/engine resources. Safe to call when nothing is loaded. */
    fun close() {}
}
