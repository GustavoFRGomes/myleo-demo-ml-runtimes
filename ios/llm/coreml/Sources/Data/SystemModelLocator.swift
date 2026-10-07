import Foundation
import FoundationModels

/// Resolves whether the system-hosted Foundation Model is usable, standing in
/// for `ModelLocator` (there is no downloadable file).
enum SystemModelLocator {
    /// A sentinel URL so the generic store treats the model as "present".
    static let sentinelURL = URL(string: "foundation-models://system")!

    static func locate() -> URL? {
        guard #available(iOS 26.0, *) else { return nil }
        if case .available = SystemLanguageModel.default.availability {
            return sentinelURL
        }
        return nil
    }

    static var hint: String {
        if #available(iOS 26.0, *) {
            return "Apple Foundation Models unavailable — enable Apple Intelligence, "
                + "or run on iOS 26+ with a supported device."
        }
        return "Apple Foundation Models require iOS 26 or later."
    }
}

/// `ChatInference` for platforms below iOS 26.
struct UnavailableInference: ChatInference {
    func loadModel() async throws -> ChatModelInfo {
        throw ChatError.loadFailed(SystemModelLocator.hint)
    }

    func generate(
        systemPrompt: String,
        messages: [ChatMessage],
        params: GenerationParams
    ) async throws -> AsyncThrowingStream<String, Error> {
        throw ChatError.loadFailed(SystemModelLocator.hint)
    }

    func cancel() async {}
    func tokenCount() async -> Int { 0 }
}

/// No-op downloader: the system model is never downloaded.
struct DisabledModelDownloader: ModelDownloading {
    func downloadModel(
        progress: @escaping @Sendable (ModelDownloadProgress) -> Void
    ) async throws -> URL {
        throw ChatError.downloadFailed("system-hosted model — no download needed")
    }

    func cancelDownload() async {}
}
