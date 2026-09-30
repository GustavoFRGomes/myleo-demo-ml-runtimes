import Foundation

/// `ModelDownloading` over Hugging Face, using a `URLSessionDownloadTask`
/// (streams to a temp file instead of holding ~800 MB in memory) with delegate
/// callbacks for progress.
final class HuggingFaceModelDownloader: NSObject, ModelDownloading, @unchecked Sendable {
    /// Repository holding the MiniCPM5-1B LiteRT bundles.
    static let repositoryBase = URL(string: "https://huggingface.co/litert-community/MiniCPM5-1B/resolve/main/")!

    private let fileName: String
    private let destinationDirectory: URL
    private let fileManager: FileManager

    private let lock = NSLock()
    private var progressHandler: (@Sendable (ModelDownloadProgress) -> Void)?
    private var continuation: CheckedContinuation<URL, Error>?
    private var task: URLSessionDownloadTask?
    private var resumed = false

    private lazy var session: URLSession = URLSession(
        configuration: .default,
        delegate: self,
        delegateQueue: nil
    )

    init(
        fileName: String = ModelLocator.preferredFileName,
        destinationDirectory: URL,
        fileManager: FileManager = .default
    ) {
        self.fileName = fileName
        self.destinationDirectory = destinationDirectory
        self.fileManager = fileManager
    }

    func downloadModel(
        progress: @escaping @Sendable (ModelDownloadProgress) -> Void
    ) async throws -> URL {
        let destination = destinationDirectory.appendingPathComponent(fileName)
        if fileManager.fileExists(atPath: destination.path) {
            progress(ModelDownloadProgress(receivedBytes: 0, totalBytes: 0))
            return destination
        }
        try fileManager.createDirectory(at: destinationDirectory, withIntermediateDirectories: true)

        return try await withCheckedThrowingContinuation { continuation in
            let url = Self.repositoryBase.appendingPathComponent(fileName)
            let task = session.downloadTask(with: url)
            lock.withLock {
                self.continuation = continuation
                self.progressHandler = progress
                self.resumed = false
                self.task = task
            }
            task.resume()
        }
    }

    func cancelDownload() async {
        let task = lock.withLock { self.task }
        task?.cancel()
    }

    // MARK: - Continuation plumbing

    private func finish(_ result: Result<URL, Error>) {
        let continuation: CheckedContinuation<URL, Error>? = lock.withLock {
            guard !resumed, let continuation else { return nil }
            resumed = true
            self.continuation = nil
            self.progressHandler = nil
            self.task = nil
            return continuation
        }
        continuation?.resume(with: result)
    }

    private func reportProgress(_ progress: ModelDownloadProgress) {
        let handler = lock.withLock { progressHandler }
        handler?(progress)
    }
}

extension HuggingFaceModelDownloader: URLSessionDownloadDelegate {
    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        reportProgress(ModelDownloadProgress(
            receivedBytes: totalBytesWritten,
            totalBytes: totalBytesExpectedToWrite
        ))
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        // The temp file is only valid inside this callback — move it now.
        let destination = destinationDirectory.appendingPathComponent(fileName)
        do {
            if fileManager.fileExists(atPath: destination.path) {
                try fileManager.removeItem(at: destination)
            }
            try fileManager.moveItem(at: location, to: destination)
        } catch {
            finish(.failure(ChatError.downloadFailed(error.localizedDescription)))
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        if let error {
            if (error as NSError).code == NSURLErrorCancelled {
                finish(.failure(CancellationError()))
            } else {
                finish(.failure(ChatError.downloadFailed(error.localizedDescription)))
            }
            return
        }
        finish(.success(destinationDirectory.appendingPathComponent(fileName)))
    }
}
