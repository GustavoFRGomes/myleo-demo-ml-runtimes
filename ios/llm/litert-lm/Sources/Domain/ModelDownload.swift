import Foundation

/// Progress of an in-app model download.
struct ModelDownloadProgress: Equatable, Sendable {
    let receivedBytes: Int64
    let totalBytes: Int64

    /// 0...1, or 0 when the server didn't report a length.
    var fraction: Double {
        guard totalBytes > 0 else { return 0 }
        return min(max(Double(receivedBytes) / Double(totalBytes), 0), 1)
    }

    var receivedMB: Double { Double(receivedBytes) / 1_048_576 }
    var totalMB: Double { Double(totalBytes) / 1_048_576 }
}

/// Download lifecycle for the model-missing state.
enum ModelDownloadState: Equatable {
    case idle
    case downloading(ModelDownloadProgress)
    case finished
    case failed(String)

    var isDownloading: Bool {
        if case .downloading = self { return true }
        return false
    }
}

/// Downloads the `.litertlm` model on first run, reporting progress. Mocked in
/// unit tests — the real implementation hits Hugging Face.
protocol ModelDownloading: Sendable {
    /// Downloads the model if absent and returns its local file URL.
    /// Cancellation surfaces as `CancellationError`.
    func downloadModel(
        progress: @escaping @Sendable (ModelDownloadProgress) -> Void
    ) async throws -> URL

    /// Cancels an in-flight download, if any.
    func cancelDownload() async
}
