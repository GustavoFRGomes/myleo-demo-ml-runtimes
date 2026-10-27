package org.ggomes.myleo.decision.dual.data

import java.io.File
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.ggomes.myleo.decision.dual.domain.DecisionQuestion
import org.ggomes.myleo.decision.dual.domain.DecisionRequest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pins the ONNX seam without the 577 MB weights file: the graph gathers the marker logits itself
 * (marker_pos/marker_mask in, one logit per option out — the author's parity.py slices
 * `candidate[i, :size]`), so [OnnxDecider] must feed the runner output straight into the decoder
 * instead of gathering by sequence position like the LiteRT lane. A fake runner therefore returns
 * exactly one logit per option; gathering those by marker position is impossible (the positions
 * exceed the option count) and fails loudly.
 */
class OnnxDeciderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeRunner(private val logits: FloatArray) : JuliaIdGraphRunner {
        override val window: Int = 1024

        override fun run(inputs: JuliaInputs.Packed): GraphRun =
            GraphRun(tokenLogits = logits, writeMs = 1.0, enqueueMs = 2.0, readbackMs = 3.0)

        override fun close() {}
    }

    private fun decider(logits: FloatArray): OnnxDecider {
        val directory = temp.newFolder("julia-1-onnx")
        val tokenizer = File(directory, "tokenizer.json").apply { writeText(tokenizerJson()) }
        return OnnxDecider(
            files =
                OnnxFiles(
                    directory = directory,
                    tokenizer = tokenizer,
                    model = File(directory, "model.onnx"),
                ),
            makeRunner = { _, _ -> FakeRunner(logits) },
        )
    }

    @Test
    fun decide_choice_scoresOptionsFromTheRunnerLogitsDirectly() = runTest {
        val decider = decider(floatArrayOf(0.4f, -0.1f))

        decider.load()
        val outcome =
            decider.decide(
                DecisionRequest(
                    state = "ab ab",
                    question = DecisionQuestion.choice("ab?", listOf("keep" to "a", "drop" to "b")),
                ),
            )

        assertThat(outcome.choiceId).isEqualTo("keep")
        assertThat(outcome.probabilities[0]).isCloseTo(0.6225, within(0.001))
        assertThat(outcome.probabilities[1]).isCloseTo(0.3775, within(0.001))
    }

    @Test
    fun decide_noul_takesTrueProbabilityFromTheSecondRunnerLogit() = runTest {
        val decider = decider(floatArrayOf(-2.0f, 2.0f))

        decider.load()
        val outcome =
            decider.decide(
                DecisionRequest(
                    state = "ab",
                    question =
                        DecisionQuestion.noul("ab?", falseText = "a", trueText = "b"),
                ),
            )

        assertThat(outcome.noul).isCloseTo(0.9820, within(0.001))
    }

    /**
     * Minimal BPE tokenizer with the five reserved tokens, the Metaspace space marker and all
     * 256 byte-fallback tokens, so any test text encodes without hitting a missing byte token.
     */
    private fun tokenizerJson(): String {
        val vocab = linkedMapOf("<pad>" to 0, "<eos>" to 1, "<bos>" to 2, "<unk>" to 3, "<mask>" to 4, "▁" to 5)
        for (byte in 0 until 256) {
            vocab["<0x" + byte.toString(16).uppercase().padStart(2, '0') + ">"] = 6 + byte
        }
        val vocabJson = vocab.entries.joinToString(",\n") { "\"${it.key}\": ${it.value}" }
        return """
            {
              "model": {
                "type": "BPE",
                "byte_fallback": true,
                "dropout": null,
                "unk_token": "<unk>",
                "ignore_merges": false,
                "vocab": {$vocabJson},
                "merges": []
              },
              "normalizer": {"type": "Replace", "pattern": {"String": " "}, "content": "▁"},
              "pre_tokenizer": {
                "type": "Metaspace",
                "replacement": "▁",
                "prepend_scheme": "always",
                "split": true
              },
              "added_tokens": [
                {"content": "<mask>", "id": 4, "normalized": false, "single_word": false, "lstrip": false, "rstrip": false}
              ]
            }
        """.trimIndent()
    }
}
