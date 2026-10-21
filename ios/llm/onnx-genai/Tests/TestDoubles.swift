import Combine
import XCTest
@testable import MyLeoLLMOnnxGenAI

/// Scripted `ChatInference` fake — no model, no runtime. Records calls so the
/// store's intent handling can be asserted.
final class FakeChatInference: ChatInference, @unchecked Sendable {
    var modelInfo = ChatModelInfo(
        displayName: "fake.litertlm",
        backend: "CPU",
        loadMs: 12,
        maxContextTokens: 0,
        sizeBytes: 4_908_692_182,
        sourceURL: "https://huggingface.co/anydaytv/minicpm5-1b-onnx-int4/resolve/main/model.onnx"
    )
    var loadError: Error?
    var generationError: Error?
    var chunks: [String] = ["Hel", "lo"]
    var tokenCounts: [Int] = [0, 7]

    private(set) var loadCount = 0
    private(set) var cancelCount = 0
    private(set) var generateCalls: [(systemPrompt: String, messages: [ChatMessage], params: GenerationParams)] = []
    private var tokenIndex = 0

    func loadModel() async throws -> ChatModelInfo {
        loadCount += 1
        if let loadError { throw loadError }
        return modelInfo
    }

    func generate(
        systemPrompt: String,
        messages: [ChatMessage],
        params: GenerationParams
    ) async throws -> AsyncThrowingStream<String, Error> {
        generateCalls.append((systemPrompt, messages, params))
        if let generationError { throw generationError }
        let chunks = self.chunks
        return AsyncThrowingStream { continuation in
            for chunk in chunks { continuation.yield(chunk) }
            continuation.finish()
        }
    }

    func cancel() async {
        cancelCount += 1
    }

    func tokenCount() async -> Int {
        defer { if tokenIndex < tokenCounts.count - 1 { tokenIndex += 1 } }
        return tokenCounts[tokenIndex]
    }
}

/// Scripted `ModelDownloading` fake — never touches the network.
final class FakeModelDownloader: ModelDownloading, @unchecked Sendable {
    var downloadError: Error?
    var progressValues: [ModelDownloadProgress] = [
        ModelDownloadProgress(receivedBytes: 2_454_346_091, totalBytes: 4_908_692_182),
        ModelDownloadProgress(receivedBytes: 4_908_692_182, totalBytes: 4_908_692_182),
    ]

    private(set) var downloadCount = 0
    private(set) var cancelCount = 0

    func downloadModel(
        progress: @escaping @Sendable (ModelDownloadProgress) -> Void
    ) async throws -> URL {
        downloadCount += 1
        for value in progressValues { progress(value) }
        if let downloadError { throw downloadError }
        return URL(fileURLWithPath: "/tmp/fake.litertlm")
    }

    func cancelDownload() async {
        cancelCount += 1
    }
}

/// Mutable model-path box so tests can simulate the file appearing.
final class PathBox: @unchecked Sendable {
    var url: URL?
    init(url: URL? = nil) { self.url = url }
}
