import Foundation
import LiteRTLM

/// `ChatInference` over LiteRT-LM's Swift `Engine`/`Conversation` API.
///
/// Each `generate` call builds a fresh `Conversation` from the full history
/// (system prompt + prior turns) and streams the reply. Rebuilding per turn
/// keeps regenerate/clear trivial at the cost of re-prefilling the history;
/// a KV-cache-reusing conversation can replace this behind the same protocol.
actor LiteRTLMEngine: ChatInference {
    private let modelURL: URL
    private let backend: Backend

    private var engine: Engine?
    private var activeConversation: Conversation?
    private var cachedInfo: ChatModelInfo?

    init(modelURL: URL, backend: Backend = .gpu) {
        self.modelURL = modelURL
        self.backend = backend
    }

    func loadModel() async throws -> ChatModelInfo {
        if let cachedInfo, await engine?.isInitialized() == true {
            return cachedInfo
        }
        let config: EngineConfig
        do {
            config = try EngineConfig(modelPath: modelURL.path, backend: backend)
        } catch {
            throw ChatError.loadFailed(error.localizedDescription)
        }
        let engine = Engine(engineConfig: config)
        let start = Date()
        do {
            try await engine.initialize()
        } catch {
            throw ChatError.loadFailed(error.localizedDescription)
        }
        let loadMs = Int64(Date().timeIntervalSince(start) * 1000)
        self.engine = engine

        // `Capabilities` (0.17.1) exposes model metadata; note it does not
        // report a max context length at this version.
        _ = Capabilities(modelPath: modelURL.path)
        let sizeBytes = (try? FileManager.default
            .attributesOfItem(atPath: modelURL.path)[.size] as? NSNumber)?.int64Value ?? 0
        let info = ChatModelInfo(
            displayName: modelURL.lastPathComponent,
            backend: Self.backendName(backend),
            loadMs: loadMs,
            maxContextTokens: 0,
            sizeBytes: sizeBytes,
            sourceURL: ModelLocator.remoteURL(for: modelURL.lastPathComponent).absoluteString
        )
        cachedInfo = info
        return info
    }

    func generate(
        systemPrompt: String,
        messages: [ChatMessage],
        params: GenerationParams
    ) async throws -> AsyncThrowingStream<String, Error> {
        guard let engine, await engine.isInitialized() else {
            throw ChatError.loadFailed("engine not initialized")
        }
        do {
            try params.validate()
        } catch {
            throw ChatError.invalidParams(error.localizedDescription)
        }

        let turns = messages.filter(\.isConversationTurn)
        guard let last = turns.last, last.role == .user else {
            throw ChatError.generationFailed("history must end with a user message")
        }

        let sampler: SamplerConfig
        do {
            sampler = try SamplerConfig(
                topK: params.topK,
                topP: Float(params.topP),
                temperature: Float(params.temperature)
            )
        } catch {
            throw ChatError.invalidParams(error.localizedDescription)
        }

        let systemText = systemPrompt.trimmingCharacters(in: .whitespacesAndNewlines)
        let config = ConversationConfig(
            systemMessage: systemText.isEmpty ? nil : Message(systemText, role: .system),
            initialMessages: turns.dropLast().map(Self.toLiteMessage),
            samplerConfig: sampler
        )

        let conversation: Conversation
        do {
            conversation = try await engine.createConversation(with: config)
        } catch {
            throw ChatError.generationFailed(error.localizedDescription)
        }
        activeConversation = conversation

        let source = conversation.sendMessageStream(
            Message(last.text, role: .user),
            maxOutputTokens: params.maxTokens
        )

        let (stream, continuation) = AsyncThrowingStream<String, Error>.makeStream()
        Task {
            do {
                for try await message in source {
                    continuation.yield(message.toString)
                }
                continuation.finish()
            } catch {
                continuation.finish(throwing: error)
            }
        }
        return stream
    }

    func cancel() async {
        try? activeConversation?.cancel()
    }

    func tokenCount() async -> Int {
        (try? activeConversation?.getTokenCount()) ?? 0
    }

    private static func toLiteMessage(_ message: ChatMessage) -> Message {
        Message(message.text, role: message.role == .assistant ? .model : .user)
    }

    private static func backendName(_ backend: Backend) -> String {
        switch backend {
        case .gpu: "GPU"
        case .cpu: "CPU"
        }
    }
}
