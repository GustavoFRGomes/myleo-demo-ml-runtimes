import Foundation

/// Metadata shown once the runtime has loaded a model (F-LLM-1).
struct ChatModelInfo: Equatable, Sendable {
    let displayName: String
    let backend: String
    let loadMs: Int64
    let maxContextTokens: Int
    /// On-disk size of the model file, in bytes.
    let sizeBytes: Int64
    /// Where the model came from (Hugging Face URL), when known.
    let sourceURL: String?
}

/// Narrow inference-engine boundary for chat generation.
///
/// Implementations wrap a runtime SDK (here LiteRT-LM) behind this protocol so
/// the store layer never touches the SDK directly, and unit tests can drive a
/// scripted fake. `generate` returns a token stream of plain text chunks;
/// `cancel` stops an in-flight generation (F-LLM-3).
protocol ChatInference: Sendable {
    /// Loads the model from its resolved location. Idempotent.
    func loadModel() async throws -> ChatModelInfo
    /// Streams an assistant response for the given system prompt + history
    /// (the last message is the prompt to answer).
    func generate(
        systemPrompt: String,
        messages: [ChatMessage],
        params: GenerationParams
    ) async throws -> AsyncThrowingStream<String, Error>
    /// Cancels the active generation, if any.
    func cancel() async
    /// Current KV-cache token count (used to derive generated-token counts).
    func tokenCount() async -> Int
}

/// User-facing chat errors.
enum ChatError: Error, Equatable, LocalizedError {
    case modelNotFound(String)
    case loadFailed(String)
    case generationFailed(String)
    case invalidParams(String)
    case downloadFailed(String)

    var errorDescription: String? {
        switch self {
        case .modelNotFound(let detail): "model not found: \(detail)"
        case .loadFailed(let detail): "failed to load model: \(detail)"
        case .generationFailed(let detail): "generation failed: \(detail)"
        case .invalidParams(let detail): "invalid parameters: \(detail)"
        case .downloadFailed(let detail): "download failed: \(detail)"
        }
    }
}
