package org.ggomes.myleo.llm.dual.data

import org.ggomes.myleo.llm.dual.domain.ChatMessage
import org.ggomes.myleo.llm.dual.domain.ChatRole

/**
 * Builds the Phi-4-mini-instruct prompt, mirroring the model's `chat_template`:
 * `<|role|>content<|end|>` per turn, then a trailing `<|assistant|>`.
 *
 * We format the prompt by hand instead of calling `Tokenizer.applyChatTemplate`: that
 * native JNI path corrupts the heap on device (`Scudo ERROR: invalid chunk state when
 * deallocating`) in ONNX Runtime GenAI 0.17.0. Tool call templates are intentionally
 * unused here.
 */
internal object Phi4MiniChatTemplate {
    fun format(systemPrompt: String, turns: List<ChatMessage>): String = buildString {
        val system = systemPrompt.trim()
        if (system.isNotEmpty()) {
            append("<|system|>").append(system).append("<|end|>")
        }
        for (turn in turns) {
            val role = if (turn.role == ChatRole.USER) "user" else "assistant"
            append("<|").append(role).append("|>").append(turn.text).append("<|end|>")
        }
        append("<|assistant|>")
    }
}
