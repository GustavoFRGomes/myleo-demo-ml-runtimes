import Foundation

/// Scripted `ChatInference` for SwiftUI previews (never used in production or
/// tests). Streams a canned reply word by word.
struct PreviewChatInference: ChatInference {
    func loadModel() async throws -> ChatModelInfo {
        ChatModelInfo(
            displayName: "MiniCPM5-1B int4 (preview)",
            backend: "CPU",
            loadMs: 1200,
            maxContextTokens: 0,
            sizeBytes: 4_908_692_182,
            sourceURL: nil
        )
    }

    func generate(
        systemPrompt: String,
        messages: [ChatMessage],
        params: GenerationParams
    ) async throws -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            for word in ["This ", "is ", "a ", "preview ", "response."] {
                continuation.yield(word)
            }
            continuation.finish()
        }
    }

    func cancel() async {}
    func tokenCount() async -> Int { 5 }
}

/// No-op downloader for previews.
struct PreviewModelDownloader: ModelDownloading {
    func downloadModel(
        progress: @escaping @Sendable (ModelDownloadProgress) -> Void
    ) async throws -> URL {
        progress(ModelDownloadProgress(receivedBytes: 4_908_692_182, totalBytes: 4_908_692_182))
        return URL(fileURLWithPath: "/tmp/preview.litertlm")
    }

    func cancelDownload() async {}
}
