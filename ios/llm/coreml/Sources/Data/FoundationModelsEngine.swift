import Foundation
import FoundationModels

/// `ChatInference` over Apple's system-hosted Foundation Model (iOS 26+).
///
/// No model download — the model ships with the OS and runs on-device via the
/// Neural Engine/GPU. Only the parameters Foundation Models supports are
/// exposed (temperature); top-k / top-p / max-tokens are ignored (documented
/// gap, see README).
@available(iOS 26.0, *)
actor FoundationModelsEngine: ChatInference {
    private var session: LanguageModelSession?
    /// Approximate generated-token count (Foundation Models exposes no token
    /// counter); used only to derive tokens/sec in the metrics.
    private var approximateTokens = 0

    func loadModel() async throws -> ChatModelInfo {
        switch SystemLanguageModel.default.availability {
        case .available:
            break
        case .unavailable(let reason):
            throw ChatError.loadFailed("system model unavailable: \(reason)")
        }
        return ChatModelInfo(
            displayName: "Apple Foundation Model (system)",
            backend: "System (ANE/GPU)",
            loadMs: 0,
            maxContextTokens: 0,
            sizeBytes: 0,
            sourceURL: "https://developer.apple.com/documentation/foundationmodels"
        )
    }

    func generate(
        systemPrompt: String,
        messages: [ChatMessage],
        params: GenerationParams
    ) async throws -> AsyncThrowingStream<String, Error> {
        do {
            try params.validate()
        } catch {
            throw ChatError.invalidParams(error.localizedDescription)
        }
        guard let last = messages.last(where: { $0.role == .user }) else {
            throw ChatError.generationFailed("no user message")
        }

        let instructions = systemPrompt.trimmingCharacters(in: .whitespacesAndNewlines)
        let session = LanguageModelSession(instructions: instructions.isEmpty ? nil : instructions)
        self.session = session

        let options = GenerationOptions(temperature: params.temperature)
        let (stream, continuation) = AsyncThrowingStream<String, Error>.makeStream()
        Task {
            do {
                var emitted = ""
                for try await snapshot in session.streamResponse(to: last.text, options: options) {
                    // ResponseStream yields cumulative snapshots — emit the delta.
                    let text = snapshot.content
                    let delta = String(text.dropFirst(emitted.count))
                    emitted = text
                    if !delta.isEmpty {
                        self.approximateTokens += delta.split(whereSeparator: \.isWhitespace).count
                        continuation.yield(delta)
                    }
                }
                continuation.finish()
            } catch {
                continuation.finish(throwing: error)
            }
        }
        return stream
    }

    func cancel() async {
        session = nil
    }

    func tokenCount() async -> Int {
        approximateTokens
    }
}
