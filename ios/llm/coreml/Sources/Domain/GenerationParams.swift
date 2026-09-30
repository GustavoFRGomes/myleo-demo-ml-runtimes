import Foundation

/// Sampling parameters exposed by the chat UI (F-LLM-2). Validated before
/// being mapped onto the runtime's sampler config so invalid values surface
/// as a user-facing error instead of a native failure.
struct GenerationParams: Equatable, Sendable {
    var temperature: Double
    var topK: Int
    var topP: Double
    var maxTokens: Int

    static let `default` = GenerationParams(
        temperature: 0.8,
        topK: 40,
        topP: 0.95,
        maxTokens: 512
    )

    enum ValidationError: Error, Equatable, LocalizedError {
        case temperatureNegative
        case topKTooSmall
        case topPOutOfRange
        case maxTokensTooSmall

        var errorDescription: String? {
            switch self {
            case .temperatureNegative: "temperature must be >= 0"
            case .topKTooSmall: "top-k must be >= 1"
            case .topPOutOfRange: "top-p must be between 0 and 1"
            case .maxTokensTooSmall: "max tokens must be >= 1"
            }
        }
    }

    func validate() throws {
        if temperature < 0 { throw ValidationError.temperatureNegative }
        if topK < 1 { throw ValidationError.topKTooSmall }
        if topP < 0 || topP > 1 { throw ValidationError.topPOutOfRange }
        if maxTokens < 1 { throw ValidationError.maxTokensTooSmall }
    }

    /// Clamps every field into the valid range. Used by the slider bindings so
    /// the UI can never produce an invalid configuration.
    func clamped() -> GenerationParams {
        GenerationParams(
            temperature: min(max(temperature, 0), 2),
            topK: min(max(topK, 1), 100),
            topP: min(max(topP, 0.05), 1),
            maxTokens: min(max(maxTokens, 1), 4096)
        )
    }
}
