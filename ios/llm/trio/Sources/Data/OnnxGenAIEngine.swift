import Foundation

/// `ChatInference` over the ONNX Runtime GenAI C API (`OgaGenerator`).
///
/// Model: Phi-4-mini-instruct **int4** (`microsoft/Phi-4-mini-instruct-onnx`,
/// `cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/`). One `OgaModel` +
/// tokenizer live for the session; each turn builds a generator, applies the
/// model's chat template, and streams decoded tokens.
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

        let info = ChatModelInfo(
            displayName: "Phi-4-mini-instruct int4 (ONNX GenAI)",
            backend: "CPU",
            loadMs: 0,
            maxContextTokens: 131072,
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

        // Build the prompt with the model's own chat template.
        let prompt = try applyChatTemplate(tokenizer: tokenizer, systemPrompt: systemPrompt, messages: messages)

        var sequences: OpaquePointer?
        try check(OgaCreateSequences(&sequences))
        guard let sequences else { throw ChatError.generationFailed("OgaCreateSequences returned nil") }
        defer { OgaDestroySequences(sequences) }
        try check(OgaTokenizerEncode(tokenizer, prompt, sequences))

        var generatorParams: OpaquePointer?
        try check(OgaCreateGeneratorParams(model, &generatorParams))
        guard let generatorParams else { throw ChatError.generationFailed("params nil") }
        defer { OgaDestroyGeneratorParams(generatorParams) }
        try check(OgaGeneratorParamsSetSearchBool(generatorParams, "do_sample", true))
        try check(OgaGeneratorParamsSetSearchNumber(generatorParams, "temperature", params.temperature))
        try check(OgaGeneratorParamsSetSearchNumber(generatorParams, "top_k", Double(params.topK)))
        try check(OgaGeneratorParamsSetSearchNumber(generatorParams, "top_p", params.topP))
        try check(OgaGeneratorParamsSetSearchNumber(generatorParams, "max_length", Double(params.maxTokens) + 1024))

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

    private func applyChatTemplate(
        tokenizer: OpaquePointer,
        systemPrompt: String,
        messages: [ChatMessage]
    ) throws -> String {
        var jsonMessages: [[String: String]] = []
        let system = systemPrompt.trimmingCharacters(in: .whitespacesAndNewlines)
        if !system.isEmpty {
            jsonMessages.append(["role": "system", "content": system])
        }
        for message in messages where message.isConversationTurn {
            jsonMessages.append([
                "role": message.role == .user ? "user" : "assistant",
                "content": message.text,
            ])
        }
        let data = try JSONSerialization.data(withJSONObject: jsonMessages)
        guard let messagesJSON = String(data: data, encoding: .utf8) else {
            throw ChatError.generationFailed("could not serialize messages")
        }
        var promptPointer: UnsafePointer<CChar>?
        // nil template → use the template shipped in the model config.
        try check(OgaTokenizerApplyChatTemplate(tokenizer, nil, messagesJSON, nil, true, &promptPointer))
        guard let promptPointer else { throw ChatError.generationFailed("chat template returned nil") }
        let prompt = String(cString: promptPointer)
        OgaDestroyString(promptPointer)
        return prompt
    }

    /// Throws when a non-null `OgaResult` carries an error, freeing it either way.
    private func check(_ result: OpaquePointer?) throws {
        guard let result else { return }
        let message = OgaResultGetError(result).map { String(cString: $0) } ?? "unknown ONNX error"
        OgaDestroyResult(result)
        throw ChatError.generationFailed(message)
    }
}
