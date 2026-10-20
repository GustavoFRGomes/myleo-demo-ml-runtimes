package org.ggomes.myleo.llm.onnxgenai.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * GenAI's `max_length` is a whole-sequence cap, so the prompt has to be added to
 * the slider's output budget — otherwise a 512-token request runs for ~1500.
 */
class OnnxGenAIEngineTest {

    @Test
    fun maxSequenceLength_promptTokens_addsPromptToTheOutputBudget() {
        assertThat(maxSequenceLength(promptTokens = 24, maxTokens = 512)).isEqualTo(536)
    }

    @Test
    fun maxSequenceLength_emptyPrompt_returnsTheOutputBudget() {
        assertThat(maxSequenceLength(promptTokens = 0, maxTokens = 512)).isEqualTo(512)
    }

    @Test
    fun maxSequenceLength_multiTurnHistory_countsTheWholePrompt() {
        assertThat(maxSequenceLength(promptTokens = 900, maxTokens = 64)).isEqualTo(964)
    }
}
