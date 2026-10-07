import Foundation

/// Scripted `ChatInference` for SwiftUI previews (never used in production or
/// tests). Streams a canned reply word by word.
struct PreviewChatInference: ChatInference {
    func loadModel() async throws -> ChatModelInfo {
        ChatModelInfo(
            displayName: "Phi-4-mini-instruct (preview)",
            backend: "GPU",
            loadMs: 1200,
            maxContextTokens: 0,
            sizeBytes: 3_910_090_752,
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
        progress(ModelDownloadProgress(receivedBytes: 3_910_090_752, totalBytes: 3_910_090_752))
        return URL(fileURLWithPath: "/tmp/preview.litertlm")
    }

    func cancelDownload() async {}
}
