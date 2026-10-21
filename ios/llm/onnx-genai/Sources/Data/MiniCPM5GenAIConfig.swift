import Foundation

/// The int4 package is an `onnxruntime-genai` **model builder** export published
/// without a `genai_config.json` (its file table lists only weights + tokenizer).
/// ONNX Runtime GenAI refuses to load a decoder without one, so we write the
/// builder's own template for this checkpoint on first use — the same file the
/// Android ONNX leaf synthesizes.
///
/// Two details matter and are why this is not just copied from an fp16 export:
///
/// - `past_present_share_buffer: true` — the graph fuses attention into
///   `GroupQueryAttention`, which writes `present` into the `past` buffer. The KV
///   cache is one static buffer sized from `max_length` instead of a fresh tensor
///   per step.
/// - no `position_ids` entry — RoPE is fused into the attention op (`do_rotary`),
///   so the graph only has `input_ids`, `attention_mask` and the past tensors.
enum MiniCPM5GenAIConfig {
    static let fileName = "genai_config.json"

    /// Checkpoint maximum, declared here and in `model.context_length`.
    static let contextLength = 131_072

    static let json = """
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
    """

    /// Writes `genai_config.json` next to the weights when missing or empty.
    /// Returns the file URL either way.
    @discardableResult
    static func ensure(in directory: URL) throws -> URL {
        let target = directory.appendingPathComponent(fileName)
        let attributes = try? FileManager.default.attributesOfItem(atPath: target.path)
        let size = (attributes?[.size] as? NSNumber)?.intValue ?? 0
        if size == 0 {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try json.write(to: target, atomically: true, encoding: .utf8)
        }
        return target
    }
}
