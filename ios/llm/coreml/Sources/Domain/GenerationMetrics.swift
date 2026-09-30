import Foundation

/// Per-generation performance report (F-LLM-4).
struct GenerationMetrics: Equatable, Sendable {
    /// Time from submitting the prompt to the first streamed token.
    let ttftMs: Int64
    /// Wall time of the whole generation (submit → stream finished).
    let totalMs: Int64
    /// Tokens generated in this turn (from the runtime's KV-cache delta).
    let generatedTokens: Int

    /// Decode speed: generated tokens over the time after the first token.
    var tokensPerSecond: Double {
        let decodeMs = totalMs - ttftMs
        guard decodeMs > 0, generatedTokens > 0 else { return 0 }
        return Double(generatedTokens) / (Double(decodeMs) / 1000)
    }
}
