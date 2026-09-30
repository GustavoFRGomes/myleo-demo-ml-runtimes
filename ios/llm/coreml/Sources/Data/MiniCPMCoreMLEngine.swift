import CoreML
import Foundation
import Tokenizers

/// `ChatInference` over the MiniCPM5-1B Core ML 4-bit `.mlpackage`.
///
/// The exported model is **stateless** with a **fixed 128-token context**:
/// input `input_ids` Int32 `[1, 128]` → `logits` Float16 `[1, 128, 130560]`.
/// Each step feeds the whole (left-aligned, padded) window and samples the next
/// token from the last real position, then slides the window. No KV cache — and
/// because the prompt shares the 128 slots, output is capped at 128 − prompt.
final class MiniCPMCoreMLEngine: ChatInference, @unchecked Sendable {
    static let contextLength = 128
    private static let eosTokenIDs: Set<Int> = [1, 130073]

    private let packageDirectory: URL
    private let sizeBytes: Int64

    private var model: MLModel?
    private var tokenizer: (any Tokenizers.Tokenizer)?
    private var cachedInfo: ChatModelInfo?
    private var generatedTokens = 0

    init(packageDirectory: URL, sizeBytes: Int64 = 0) {
        self.packageDirectory = packageDirectory
        self.sizeBytes = sizeBytes
    }

    func loadModel() async throws -> ChatModelInfo {
        if let cachedInfo, model != nil { return cachedInfo }
        let start = Date()

        let mlpackage = packageDirectory.appendingPathComponent(MiniCPMModelLocator.packageName)
        let compiledURL = try await compiledModelURL(mlpackage: mlpackage)
        let configuration = MLModelConfiguration()
        configuration.computeUnits = .cpuAndNeuralEngine
        do {
            model = try MLModel(contentsOf: compiledURL, configuration: configuration)
        } catch {
            throw ChatError.loadFailed(error.localizedDescription)
        }
        do {
            tokenizer = try await AutoTokenizer.from(modelFolder: packageDirectory)
        } catch {
            throw ChatError.loadFailed("tokenizer: \(error.localizedDescription)")
        }
        guard tokenizer != nil else { throw ChatError.loadFailed("tokenizer not found") }

        let loadMs = Int64(Date().timeIntervalSince(start) * 1000)
        let info = ChatModelInfo(
            displayName: "MiniCPM5-1B 4-bit (Core ML)",
            backend: "Core ML (ANE/GPU/CPU)",
            loadMs: loadMs,
            maxContextTokens: Self.contextLength,
            sizeBytes: sizeBytes,
            sourceURL: MiniCPMModelLocator.sourceURL
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

        let prompt = buildPrompt(systemPrompt: systemPrompt, messages: messages)
        var window = tokenizer.encode(text: prompt).map(Int32.init)
        guard !window.isEmpty else { throw ChatError.generationFailed("empty prompt") }

        let (stream, continuation) = AsyncThrowingStream<String, Error>.makeStream()
        Task {
            await self.runGeneration(window: window, params: params, continuation: continuation)
        }
        return stream
    }

    private func runGeneration(
        window: [Int32],
        params: GenerationParams,
        continuation: AsyncThrowingStream<String, Error>.Continuation
    ) async {
        guard let model, let tokenizer else {
            continuation.finish(throwing: ChatError.loadFailed("not initialized"))
            return
        }
        var window = window
        do {
            var produced = 0
            while produced < params.maxTokens {
                if Task.isCancelled { break }
                // Left-align + pad to the fixed width.
                var ids = Array(window.suffix(Self.contextLength))
                let realLength = ids.count
                if ids.count < Self.contextLength {
                    ids += Array(repeating: Int32(1), count: Self.contextLength - ids.count)
                }
                let input = try MLMultiArray(
                    shape: [NSNumber(value: 1), NSNumber(value: Self.contextLength)],
                    dataType: .int32
                )
                for (i, value) in ids.enumerated() { input[i] = NSNumber(value: value) }

                let provider = try MLDictionaryFeatureProvider(
                    dictionary: ["input_ids": MLFeatureValue(multiArray: input)]
                )
                // Synchronous prediction keeps MLModel actor-local (it is not Sendable).
                let prediction = try await model.prediction(from: provider)
                guard let logits = prediction.featureValue(for: "logits")?.multiArrayValue else {
                    throw ChatError.generationFailed("no logits output")
                }
                let row = Self.logitsRow(logits, position: realLength - 1)
                let next = Self.sample(logits: row, params: params)
                if Self.eosTokenIDs.contains(Int(next)) { break }

                window.append(Int32(next))
                produced += 1
                generatedTokens += 1
                let piece = tokenizer.decode(tokens: [Int(next)])
                if !piece.isEmpty { continuation.yield(piece) }
            }
            continuation.finish()
        } catch {
            continuation.finish(throwing: error)
        }
    }

    func cancel() async {}

    func tokenCount() async -> Int { generatedTokens }

    // MARK: - Compilation cache

    private func compiledModelURL(mlpackage: URL) async throws -> URL {
        let cacheDir = packageDirectory.appendingPathComponent("compiled", isDirectory: true)
        let cached = cacheDir.appendingPathComponent("MiniCPM5-1B-CoreML-4bit.mlmodelc")
        if FileManager.default.fileExists(atPath: cached.path) { return cached }
        do {
            let compiled = try await MLModel.compileModel(at: mlpackage)
            try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
            try? FileManager.default.removeItem(at: cached)
            try FileManager.default.copyItem(at: compiled, to: cached)
            return cached
        } catch {
            throw ChatError.loadFailed("compile: \(error.localizedDescription)")
        }
    }

    // MARK: - Prompt + sampling

    private func buildPrompt(systemPrompt: String, messages: [ChatMessage]) -> String {
        var prompt = ""
        let system = systemPrompt.trimmingCharacters(in: .whitespacesAndNewlines)
        if !system.isEmpty {
            prompt += "<|im_start|>system\n\(system)<|im_end|>\n"
        }
        for message in messages where message.isConversationTurn {
            let role = message.role == .user ? "user" : "assistant"
            prompt += "<|im_start|>\(role)\n\(message.text)<|im_end|>\n"
        }
        prompt += "<|im_start|>assistant\n"
        return prompt
    }

    /// Reads the Float16 logit row for `position` from a `[1, 128, vocab]` array.
    private static func logitsRow(_ logits: MLMultiArray, position: Int) -> [Float] {
        let vocab = logits.shape.last?.intValue ?? 0
        let strides = logits.strides.map { $0.intValue }
        let pointer = logits.dataPointer.bindMemory(to: Float16.self, capacity: logits.count)
        let base = position * (strides.count > 1 ? strides[1] : vocab)
        let dimensionStride = strides.last ?? 1
        var row = [Float](repeating: 0, count: vocab)
        for i in 0..<vocab {
            row[i] = Float(pointer[base + i * dimensionStride])
        }
        return row
    }

    /// Temperature → top-k → top-p sampling. Pure — unit-tested.
    static func sample(logits: [Float], params: GenerationParams, uniform: () -> Double = { Double.random(in: 0..<1) }) -> Int {
        guard !logits.isEmpty else { return 0 }
        let temperature = max(params.temperature, 0.0001)
        var indexed = logits.enumerated().map { (index: $0.offset, logit: Double($0.element) / temperature) }
        indexed.sort { $0.logit > $1.logit }

        var candidates = Array(indexed.prefix(max(params.topK, 1)))
        let maxLogit = candidates.first?.logit ?? 0
        var weights = candidates.map { exp($0.logit - maxLogit) }
        let total = weights.reduce(0, +)
        if total <= 0 || !total.isFinite { return candidates.first?.index ?? 0 }

        // Nucleus (top-p): keep the smallest set whose cumulative mass ≥ topP.
        let normalized = weights.map { $0 / total }
        var cumulative = 0.0
        var cutoff = candidates.count
        for (i, weight) in normalized.enumerated() {
            cumulative += weight
            if cumulative >= params.topP { cutoff = i + 1; break }
        }
        candidates = Array(candidates.prefix(cutoff))
        weights = Array(normalized.prefix(cutoff))
        let mass = weights.reduce(0, +)
        guard mass > 0 else { return candidates.first?.index ?? 0 }

        var threshold = uniform() * mass
        for (i, weight) in weights.enumerated() {
            threshold -= weight
            if threshold <= 0 { return candidates[i].index }
        }
        return candidates.last?.index ?? 0
    }
}
