import Foundation

/// Builds the MiniCPM5-1B prompt, mirroring the model's `chat_template.jinja`:
///
/// ```
/// <s><|im_start|>system\n{sys}<|im_end|>\n
/// <|im_start|>user\n{text}<|im_end|>\n
/// <|im_start|>assistant\n<think>\n\n</think>\n\n
/// ```
///
/// The leading `<s>` is part of the rendered template: ORTX's `encode` does not add
/// a BOS of its own, so dropping it would drop the BOS token entirely.
///
/// The empty `<think></think>` block disables reasoning — exactly what
/// `chat_template.jinja` emits for `enable_thinking=false`, matching the LiteRT-LM
/// leaves, which run with thinking off unless asked.
///
/// Formatted by hand instead of `OgaTokenizerApplyChatTemplate`: that native path
/// corrupts the heap on device in ONNX Runtime GenAI 0.17.0 (Android saw
/// `Scudo ERROR: invalid chunk state when deallocating`, and the iOS leg never used
/// it for MiniCPM). Tool calling is intentionally unused.
enum MiniCPM5ChatTemplate {
    private static let bos = "<s>"
    private static let imStart = "<|im_start|>"
    private static let imEnd = "<|im_end|>"
    private static let noThink = "<think>\n\n</think>\n\n"

    static func format(systemPrompt: String, turns: [ChatMessage]) -> String {
        var prompt = bos
        let system = systemPrompt.trimmingCharacters(in: .whitespacesAndNewlines)
        if !system.isEmpty {
            prompt += "\(imStart)system\n\(system)\(imEnd)\n"
        }
        for turn in turns {
            let role = turn.role == .user ? "user" : "assistant"
            prompt += "\(imStart)\(role)\n\(turn.text)\(imEnd)\n"
        }
        prompt += "\(imStart)assistant\n\(noThink)"
        return prompt
    }
}
