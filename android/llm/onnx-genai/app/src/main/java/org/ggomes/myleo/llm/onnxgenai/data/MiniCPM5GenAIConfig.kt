package org.ggomes.myleo.llm.onnxgenai.data

import java.io.File

/**
 * The int4 package is an `onnxruntime-genai` **model builder** export, but it is published
 * without a `genai_config.json` (its file table lists only weights + tokenizer). ONNX Runtime
 * GenAI refuses to load a decoder without one, so we write the builder's own template for
 * this checkpoint on first use.
 *
 * Two details matter and are the reason this is not just copied from the fp16 export:
 *
 * - `past_present_share_buffer: true` — the graph fuses attention into `GroupQueryAttention`,
 *   which writes `present` into the `past` buffer. The KV cache is therefore one static
 *   buffer sized from `max_length`, instead of a fresh growing tensor per step.
 * - no `position_ids` entry — RoPE is fused into the attention op (`do_rotary = 1`), so the
 *   graph has only `input_ids`, `attention_mask` and the past tensors.
 */
object MiniCPM5GenAIConfig {
    const val FILE_NAME = "genai_config.json"

    val JSON: String =
        """
        {
            "model": {
                "bos_token_id": 0,
                "context_length": 131072,
                "decoder": {
                    "session_options": {
                        "log_id": "onnxruntime-genai",
                        "provider_options": []
                    },
                    "filename": "model.onnx",
                    "head_size": 128,
                    "hidden_size": 1536,
                    "inputs": {
                        "input_ids": "input_ids",
                        "attention_mask": "attention_mask",
                        "past_key_names": "past_key_values.%d.key",
                        "past_value_names": "past_key_values.%d.value"
                    },
                    "outputs": {
                        "logits": "logits",
                        "present_key_names": "present.%d.key",
                        "present_value_names": "present.%d.value"
                    },
                    "num_attention_heads": 16,
                    "num_hidden_layers": 24,
                    "num_key_value_heads": 2
                },
                "eos_token_id": [1, 130073],
                "pad_token_id": 1,
                "type": "llama",
                "vocab_size": 130560
            },
            "search": {
                "diversity_penalty": 0.0,
                "do_sample": true,
                "early_stopping": true,
                "length_penalty": 1.0,
                "max_length": 131072,
                "min_length": 0,
                "no_repeat_ngram_size": 0,
                "num_beams": 1,
                "num_return_sequences": 1,
                "past_present_share_buffer": true,
                "repetition_penalty": 1.0,
                "temperature": 0.9,
                "top_k": 50,
                "top_p": 0.95
            }
        }
        """.trimIndent()

    /** Writes [FILE_NAME] next to the weights when it is missing or empty. */
    fun ensure(dir: File): File {
        val target = File(dir, FILE_NAME)
        if (!target.exists() || target.length() == 0L) {
            dir.mkdirs()
            target.writeText(JSON)
        }
        return target
    }
}
