package org.ggomes.myleo.llm.onnxgenai.data

import org.assertj.core.api.Assertions.assertThat
import org.ggomes.myleo.llm.onnxgenai.domain.ChatMessage
import org.ggomes.myleo.llm.onnxgenai.domain.ChatRole
import org.junit.Test

class Phi4MiniChatTemplateTest {

    @Test
    fun format_systemAndOneUserTurn_wrapsEachTurnAndAddsAssistantPrompt() {
        val out = Phi4MiniChatTemplate.format(
            systemPrompt = "Be brief.",
            turns = listOf(ChatMessage(role = ChatRole.USER, text = "Hi")),
        )

        assertThat(out).isEqualTo(
            "<|system|>Be brief.<|end|><|user|>Hi<|end|><|assistant|>",
        )
    }

    @Test
    fun format_blankSystem_omitsSystemTurn() {
        val out = Phi4MiniChatTemplate.format(
            systemPrompt = "   ",
            turns = listOf(ChatMessage(role = ChatRole.USER, text = "Hi")),
        )

        assertThat(out).isEqualTo("<|user|>Hi<|end|><|assistant|>")
        assertThat(out).doesNotContain("<|system|>")
    }

    @Test
    fun format_multiTurn_mapsAssistantRoleAndEndsWithAssistantPrompt() {
        val out = Phi4MiniChatTemplate.format(
            systemPrompt = "S",
            turns = listOf(
                ChatMessage(role = ChatRole.USER, text = "q1"),
                ChatMessage(role = ChatRole.ASSISTANT, text = "a1"),
                ChatMessage(role = ChatRole.USER, text = "q2"),
            ),
        )

        assertThat(out).isEqualTo(
            "<|system|>S<|end|>" +
                "<|user|>q1<|end|>" +
                "<|assistant|>a1<|end|>" +
                "<|user|>q2<|end|>" +
                "<|assistant|>",
        )
    }
}
