import Foundation

/// Location + Hugging Face delivery for the MiniCPM5-1B **Core ML 4-bit** model
/// (`Reza2kn/MiniCPM5-1B-CoreML-4bit`, Apache-2.0).
enum MiniCPMModelLocator {
    static let folderName = "minicpm5-1b-coreml-4bit"
    static let packageName = "MiniCPM5-1B-CoreML-4bit.mlpackage"
    static let repo = "Reza2kn/MiniCPM5-1B-CoreML-4bit"

    /// Paths (relative to the package dir) to download: name → destination.
    static let files: [(name: String, path: String)] = [
        ("Manifest.json", "\(packageName)/Manifest.json"),
        ("model.mlmodel", "\(packageName)/Data/com.apple.CoreML/model.mlmodel"),
        ("weight.bin", "\(packageName)/Data/com.apple.CoreML/weights/weight.bin"),
        ("tokenizer.json", "tokenizer.json"),
        ("tokenizer_config.json", "tokenizer_config.json"),
        ("chat_template.jinja", "chat_template.jinja"),
        ("config.json", "config.json"),
        ("coreml_config.json", "coreml_config.json"),
    ]

    static func packageDirectory(fileManager: FileManager = .default) -> URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Models", isDirectory: true)
            .appendingPathComponent(folderName, isDirectory: true)
    }

    /// The `.mlpackage` URL when all required files exist.
    static func locate(fileManager: FileManager = .default) -> URL? {
        let dir = packageDirectory(fileManager: fileManager)
        let required = ["\(packageName)/Manifest.json", "tokenizer.json", "tokenizer_config.json"]
        for path in required where !fileManager.fileExists(atPath: dir.appendingPathComponent(path).path) {
            return nil
        }
        return dir
    }

    static func sourceURL(for name: String) -> URL {
        URL(string: "https://huggingface.co/\(repo)/resolve/main/\(name)")!
    }

    static func packageSize(fileManager: FileManager = .default) -> Int64 {
        let root = packageDirectory(fileManager: fileManager)
        guard let enumerator = fileManager.enumerator(at: root, includingPropertiesForKeys: [.fileSizeKey]) else {
            return 0
        }
        var total: Int64 = 0
        for case let url as URL in enumerator {
            total += Int64((try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0)
        }
        return total
    }

    static var hint: String {
        "Download MiniCPM5-1B (Core ML 4-bit, ~619 MB) — the .mlpackage + tokenizer from Hugging Face."
    }

    static var sourceURL: String {
        URL(string: "https://huggingface.co/\(repo)")!.absoluteString
    }
}

/// Downloads the CoreML package (nested paths preserved) with aggregate byte
/// progress. Source-of-truth URL list comes from `MiniCPMModelLocator.files`.
final class MiniCPMFileSetDownloader: NSObject, ModelDownloading, @unchecked Sendable {
    private let destinationDirectory: URL
    private let files: [(name: String, path: String)]
    private let fileManager: FileManager

    private let lock = NSLock()
    private var progressHandler: (@Sendable (ModelDownloadProgress) -> Void)?
    private var continuation: CheckedContinuation<URL, Error>?
    private var index = 0
    private var completedBytes: Int64 = 0
    private var currentExpected: Int64 = 0
    private var currentDestination: URL?
    private var resumed = false
    private var session: URLSession!

    init(
        destinationDirectory: URL = MiniCPMModelLocator.packageDirectory(),
        files: [(name: String, path: String)] = MiniCPMModelLocator.files,
        fileManager: FileManager = .default
    ) {
        self.destinationDirectory = destinationDirectory
        self.files = files
        self.fileManager = fileManager
        super.init()
        session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
    }

    func downloadModel(
        progress: @escaping @Sendable (ModelDownloadProgress) -> Void
    ) async throws -> URL {
        try fileManager.createDirectory(at: destinationDirectory, withIntermediateDirectories: true)
        return try await withCheckedThrowingContinuation { continuation in
            lock.withLock {
                self.continuation = continuation
                self.progressHandler = progress
                self.index = 0
                self.completedBytes = 0
                self.resumed = false
            }
            startNextFile()
        }
    }

    func cancelDownload() async {
        session.getAllTasks { tasks in tasks.forEach { $0.cancel() } }
    }

    private func startNextFile() {
        let next: (name: String, path: String)? = lock.withLock {
            guard index < files.count else { return nil }
            return files[index]
        }
        guard let next else {
            finish(.success(destinationDirectory))
            return
        }
        let destination = destinationDirectory.appendingPathComponent(next.path)
        if fileManager.fileExists(atPath: destination.path) {
            lock.withLock { completedBytes += Self.fileSize(destination) }
            advance()
            return
        }
        try? fileManager.createDirectory(
            at: destination.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        lock.withLock { currentDestination = destination }
        session.downloadTask(with: MiniCPMModelLocator.sourceURL(for: next.name)).resume()
    }

    private func advance() {
        lock.withLock { index += 1 }
        startNextFile()
    }

    private static func fileSize(_ url: URL) -> Int64 {
        ((try? FileManager.default.attributesOfItem(atPath: url.path)[.size]) as? NSNumber)?.int64Value ?? 0
    }

    private func report(received: Int64, expected: Int64) {
        let handler = lock.withLock { progressHandler }
        handler?(ModelDownloadProgress(receivedBytes: received, totalBytes: expected))
    }

    private func finish(_ result: Result<URL, Error>) {
        let continuation: CheckedContinuation<URL, Error>? = lock.withLock {
            guard !resumed, let continuation else { return nil }
            resumed = true
            self.continuation = nil
            self.progressHandler = nil
            return continuation
        }
        continuation?.resume(with: result)
    }
}

extension MiniCPMFileSetDownloader: URLSessionDownloadDelegate {
    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64,
        totalBytesExpectedToWrite: Int64
    ) {
        let (completed, expected): (Int64, Int64) = lock.withLock {
            currentExpected = max(totalBytesExpectedToWrite, totalBytesWritten)
            return (completedBytes, completedBytes + currentExpected)
        }
        report(received: completed + totalBytesWritten, expected: expected)
    }

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        guard let destination = lock.withLock({ currentDestination }) else { return }
        do {
            if fileManager.fileExists(atPath: destination.path) {
                try fileManager.removeItem(at: destination)
            }
            try fileManager.moveItem(at: location, to: destination)
            lock.withLock { completedBytes += Self.fileSize(destination) }
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
        guard (task.response as? HTTPURLResponse).map({ $0.statusCode < 400 }) ?? true else {
            let code = (task.response as? HTTPURLResponse)?.statusCode ?? -1
            finish(.failure(ChatError.downloadFailed("HTTP \(code) for \(task.currentRequest?.url?.lastPathComponent ?? "?")")))
            return
        }
        advance()
    }
}
