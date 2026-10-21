import Foundation

/// `ChatInference` over the ONNX Runtime GenAI C API (`OgaGenerator`).
///
/// Model: MiniCPM5-1B **int4** (`anydaytv/minicpm5-1b-onnx-int4`) — the same
/// package the Android ONNX leaf uses. One `OgaModel` + tokenizer live for the
/// session; each turn builds a generator, encodes a hand-built ChatML prompt
/// (`MiniCPM5ChatTemplate`), and streams decoded tokens.
actor OnnxGenAIEngine: ChatInference {
    private let packageDirectory: URL
    private let sizeBytes: Int64

    private var model: OpaquePointer?
    private var tokenizer: OpaquePointer?
    private var generator: OpaquePointer?
    private var cachedInfo: ChatModelInfo?
    private var generatedTokens = 0

    init(packageDirectory: URL, sizeBytes: Int64 = 0) {
        self.packageDirectory = packageDirectory
        self.sizeBytes = sizeBytes
    }

    func loadModel() async throws -> ChatModelInfo {
        if let cachedInfo, model != nil { return cachedInfo }

        // The published package has no `genai_config.json` and carries the one
        // ORTX-incompatible regex in `tokenizer.json`; both are repaired here so
        // downloaded and sideloaded copies behave the same. Idempotent.
        do {
            try MiniCPM5GenAIConfig.ensure(in: packageDirectory)
            try TokenizerPatch.apply(to: packageDirectory.appendingPathComponent("tokenizer.json"))
        } catch {
            throw ChatError.loadFailed("could not prepare ONNX package: \(error.localizedDescription)")
        }

        let started = Date()
        var model: OpaquePointer?
        do {
            try check(OgaCreateModel(packageDirectory.path, &model))
        } catch {
            throw ChatError.loadFailed(error.localizedDescription)
        }
        guard let model else { throw ChatError.loadFailed("OgaCreateModel returned nil") }
        self.model = model

        var tokenizer: OpaquePointer?
        do {
            try check(OgaCreateTokenizer(model, &tokenizer))
        } catch {
            throw ChatError.loadFailed(error.localizedDescription)
        }
        self.tokenizer = tokenizer
        let loadMs = Int64(Date().timeIntervalSince(started) * 1000)

        let info = ChatModelInfo(
            displayName: "MiniCPM5-1B int4 (ONNX GenAI)",
            backend: "CPU",
            loadMs: loadMs,
            maxContextTokens: MiniCPM5GenAIConfig.contextLength,
            sizeBytes: sizeBytes,
            sourceURL: OnnxGenAIModelLocator.primarySourceURL
        )
        cachedInfo = info
        return info
    }

    func generate(
        systemPrompt: String,
        messages: [ChatMessage],
        params: GenerationParams
    ) async throws -> AsyncThrowingStream<String, Error> {
        guard let model, let tokenizer else { throw ChatError.loadFailed("not initialized") }
        do {
            try params.validate()
        } catch {
            throw ChatError.invalidParams(error.localizedDescription)
        }

        // Hand-built ChatML prompt: `OgaTokenizerApplyChatTemplate` corrupts the
        // heap on device in 0.17.0 (see MiniCPM5ChatTemplate).
        let turns = messages.filter(\.isConversationTurn)
        guard let last = turns.last, last.role == .user else {
            throw ChatError.generationFailed("history must end with a user message")
        }
        let prompt = MiniCPM5ChatTemplate.format(systemPrompt: systemPrompt, turns: turns)

        var sequences: OpaquePointer?
        try check(OgaCreateSequences(&sequences))
        guard let sequences else { throw ChatError.generationFailed("OgaCreateSequences returned nil") }
        defer { OgaDestroySequences(sequences) }
        try check(OgaTokenizerEncode(tokenizer, prompt, sequences))
        let promptTokens = Int(OgaSequencesGetSequenceCount(sequences, 0))
        // Same accounting as the other runtime: the KV-cache delta includes the
        // prompt, so `tok/s` is comparable with the LiteRT-LM numbers.
        generatedTokens = promptTokens

        var generatorParams: OpaquePointer?
        try check(OgaCreateGeneratorParams(model, &generatorParams))
        guard let generatorParams else { throw ChatError.generationFailed("params nil") }
        defer { OgaDestroyGeneratorParams(generatorParams) }
        try check(OgaGeneratorParamsSetSearchBool(generatorParams, "do_sample", true))
        try check(OgaGeneratorParamsSetSearchNumber(generatorParams, "temperature", params.temperature))
        try check(OgaGeneratorParamsSetSearchNumber(generatorParams, "top_k", Double(params.topK)))
        try check(OgaGeneratorParamsSetSearchNumber(generatorParams, "top_p", params.topP))
        try check(OgaGeneratorParamsSetSearchNumber(generatorParams, "max_length", Double(promptTokens + params.maxTokens)))

        var generator: OpaquePointer?
        try check(OgaCreateGenerator(model, generatorParams, &generator))
        guard let generator else { throw ChatError.generationFailed("generator nil") }
        self.generator = generator
        try check(OgaGenerator_AppendTokenSequences(generator, sequences))

        var tokenizerStream: OpaquePointer?
        try check(OgaCreateTokenizerStream(tokenizer, &tokenizerStream))

        let (stream, continuation) = AsyncThrowingStream<String, Error>.makeStream()
        Task {
            defer {
                if let tokenizerStream { OgaDestroyTokenizerStream(tokenizerStream) }
                OgaDestroyGenerator(generator)
            }
            do {
                while !OgaGenerator_IsDone(generator) {
                    if Task.isCancelled { break }
                    try self.check(OgaGenerator_GenerateNextToken(generator))
                    var tokens: UnsafePointer<Int32>?
                    var count = 0
                    try self.check(OgaGenerator_GetNextTokens(generator, &tokens, &count))
                    guard let tokens, count > 0 else { continue }
                    self.generatedTokens += Int(count)
                    for i in 0..<Int(count) {
                        var text: UnsafePointer<CChar>?
                        if let stream = tokenizerStream {
                            try self.check(OgaTokenizerStreamDecode(stream, tokens[i], &text))
                        }
                        if let text, text.pointee != 0 {
                            continuation.yield(String(cString: text))
                        }
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
        // The generation Task observes Task.isCancelled, breaks its loop and
        // destroys the generator in its defer.
        generator = nil
    }

    func tokenCount() async -> Int {
        generatedTokens
    }

    // MARK: - Helpers

    /// Throws when a non-null `OgaResult` carries an error, freeing it either way.
    private func check(_ result: OpaquePointer?) throws {
        guard let result else { return }
        let message = OgaResultGetError(result).map { String(cString: $0) } ?? "unknown ONNX error"
        OgaDestroyResult(result)
        throw ChatError.generationFailed(message)
    }
}
