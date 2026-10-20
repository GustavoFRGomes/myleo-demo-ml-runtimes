package org.ggomes.myleo.llm.onnxgenai.data

import org.ggomes.myleo.llm.onnxgenai.domain.ChatMessage
import org.ggomes.myleo.llm.onnxgenai.domain.ChatRole

/**
 * Builds the MiniCPM5-1B prompt, mirroring the model's `chat_template.jinja`:
 *
 * ```
 * <s><|im_start|>system\n{sys}<|im_end|>\n
 * <|im_start|>user\n{text}<|im_end|>\n
 * <|im_start|>assistant\n<think>\n\n</think>\n\n
 * ```
 *
 * The leading `<s>` is part of the rendered template: ORTX's `encode` does not add a
 * BOS of its own (`encode("hello")` returns a single non-zero id), so dropping it
 * would drop the BOS token entirely.
 *
 * The empty `<think></think>` block disables reasoning — it is exactly what
 * `chat_template.jinja` emits for `enable_thinking=false`, and matches the LiteRT-LM
 * leaves, which run with thinking off unless asked.
 *
 * We format the prompt by hand instead of calling `Tokenizer.applyChatTemplate`: that
 * native JNI path corrupts the heap on device (`Scudo ERROR: invalid chunk state when
 * deallocating`) in ONNX Runtime GenAI 0.17.0. Tool calling is intentionally unused.
 */
internal object MiniCPM5ChatTemplate {
    private const val BOS = "<s>"
    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"
    private const val NO_THINK = "<think>\n\n</think>\n\n"

    fun format(systemPrompt: String, turns: List<ChatMessage>): String = buildString {
        append(BOS)
        val system = systemPrompt.trim()
        if (system.isNotEmpty()) {
            append(IM_START).append("system\n").append(system).append(IM_END).append('\n')
        }
        for (turn in turns) {
            val role = if (turn.role == ChatRole.USER) "user" else "assistant"
            append(IM_START).append(role).append('\n').append(turn.text).append(IM_END).append('\n')
        }
        append(IM_START).append("assistant\n").append(NO_THINK)
    }
}
