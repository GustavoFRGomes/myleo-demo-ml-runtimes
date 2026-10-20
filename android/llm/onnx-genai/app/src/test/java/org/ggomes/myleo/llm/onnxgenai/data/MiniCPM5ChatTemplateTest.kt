package org.ggomes.myleo.llm.onnxgenai.data

import org.assertj.core.api.Assertions.assertThat
import org.ggomes.myleo.llm.onnxgenai.domain.ChatMessage
import org.ggomes.myleo.llm.onnxgenai.domain.ChatRole
import org.junit.Test

/**
 * Pins the exact bytes the model's `chat_template.jinja` produces for
 * `enable_thinking=false`; a stray space or newline changes the token ids and the
 * model answers differently.
 */
class MiniCPM5ChatTemplateTest {

    @Test
    fun format_systemAndOneUserTurn_matchesTheChatTemplate() {
        val out = MiniCPM5ChatTemplate.format(
            systemPrompt = "Be brief.",
            turns = listOf(ChatMessage(role = ChatRole.USER, text = "Hi")),
        )

        assertThat(out).isEqualTo(
            "<s>" +
                "<|im_start|>system\nBe brief.<|im_end|>\n" +
                "<|im_start|>user\nHi<|im_end|>\n" +
                "<|im_start|>assistant\n<think>\n\n</think>\n\n",
        )
    }

    @Test
    fun format_blankSystem_omitsSystemBlock() {
        val out = MiniCPM5ChatTemplate.format(
            systemPrompt = "   ",
            turns = listOf(ChatMessage(role = ChatRole.USER, text = "Hi")),
        )

        assertThat(out).isEqualTo(
            "<s>" +
                "<|im_start|>user\nHi<|im_end|>\n" +
                "<|im_start|>assistant\n<think>\n\n</think>\n\n",
        )
        assertThat(out).doesNotContain("system")
    }

    @Test
    fun format_multiTurn_keepsTurnOrderAndRoles() {
        val out = MiniCPM5ChatTemplate.format(
            systemPrompt = "S",
            turns = listOf(
                ChatMessage(role = ChatRole.USER, text = "q1"),
                ChatMessage(role = ChatRole.ASSISTANT, text = "a1"),
                ChatMessage(role = ChatRole.USER, text = "q2"),
            ),
        )

        assertThat(out).isEqualTo(
            "<s>" +
                "<|im_start|>system\nS<|im_end|>\n" +
                "<|im_start|>user\nq1<|im_end|>\n" +
                "<|im_start|>assistant\na1<|im_end|>\n" +
                "<|im_start|>user\nq2<|im_end|>\n" +
                "<|im_start|>assistant\n<think>\n\n</think>\n\n",
        )
    }

    @Test
    fun format_always_startsWithExactlyOneBos() {
        val out = MiniCPM5ChatTemplate.format(
            systemPrompt = "",
            turns = listOf(ChatMessage(role = ChatRole.USER, text = "Hi")),
        )

        assertThat(out).startsWith("<s><|im_start|>")
        assertThat(out.split("<s>")).hasSize(2) // one occurrence
    }

    @Test
    fun format_always_opensAnEmptyAssistantTurnWithThinkingDisabled() {
        val out = MiniCPM5ChatTemplate.format(
            systemPrompt = "",
            turns = listOf(ChatMessage(role = ChatRole.USER, text = "Hi")),
        )

        assertThat(out).endsWith("<|im_start|>assistant\n<think>\n\n</think>\n\n")
    }
}
