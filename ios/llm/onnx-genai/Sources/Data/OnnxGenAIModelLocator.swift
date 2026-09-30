import Foundation

/// Location + Hugging Face delivery for the MiniCPM5-1B **int4** ONNX Runtime
/// GenAI model (`anydaytv/minicpm5-1b-onnx-int4`, with the two config files the
/// int4 repo omits supplied from `onnx-community/MiniCPM5-1B`).
enum OnnxGenAIModelLocator {
    /// Subdirectory under Documents/Models holding the whole GenAI package.
    static let folderName = "minicpm5-1b-onnx-int4"
    static let configFileName = "genai_config.json"
    static let weightsFileName = "model.onnx"

    /// Files to fetch: name → Hugging Face repo. Two repos because the int4
    /// export lacks `genai_config.json` / `chat_template.jinja`.
    static let files: [(name: String, repo: String)] = [
        ("model.onnx", "anydaytv/minicpm5-1b-onnx-int4"),
        ("model.onnx.data", "anydaytv/minicpm5-1b-onnx-int4"),
        ("tokenizer.json", "anydaytv/minicpm5-1b-onnx-int4"),
        ("tokenizer_config.json", "anydaytv/minicpm5-1b-onnx-int4"),
        ("genai_config.json", "onnx-community/MiniCPM5-1B"),
        ("chat_template.jinja", "onnx-community/MiniCPM5-1B"),
    ]

    static func modelsRootDirectory(fileManager: FileManager = .default) -> URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Models", isDirectory: true)
    }

    static func packageDirectory(fileManager: FileManager = .default) -> URL {
        modelsRootDirectory(fileManager: fileManager)
            .appendingPathComponent(folderName, isDirectory: true)
    }

    /// Returns the package directory when every required file exists.
    static func locate(fileManager: FileManager = .default) -> URL? {
        let dir = packageDirectory(fileManager: fileManager)
        let required = [configFileName, weightsFileName, "tokenizer.json", "tokenizer_config.json"]
        for name in required where !fileManager.fileExists(atPath: dir.appendingPathComponent(name).path) {
            return nil
        }
        return dir
    }

    static func sourceURL(for name: String, repo: String) -> URL {
        URL(string: "https://huggingface.co/\(repo)/resolve/main/\(name)")!
    }

    /// Total on-disk size of the downloaded package (0 when absent).
    static func packageSize(fileManager: FileManager = .default) -> Int64 {
        let dir = packageDirectory(fileManager: fileManager)
        let names = (try? fileManager.contentsOfDirectory(atPath: dir.path)) ?? []
        return names.reduce(0) { total, name in
            let path = dir.appendingPathComponent(name).path
            let size = (try? fileManager.attributesOfItem(atPath: path)[.size] as? NSNumber)?.int64Value ?? 0
            return total + size
        }
    }

    static var hint: String {
        "Download the MiniCPM5-1B int4 ONNX package (~1.4 GB) — 6 files from Hugging Face "
            + "(anydaytv/minicpm5-1b-onnx-int4 + onnx-community/MiniCPM5-1B)."
    }

    static var primarySourceURL: String {
        sourceURL(for: "model.onnx", repo: "anydaytv/minicpm5-1b-onnx-int4").absoluteString
    }
}

/// Downloads the multi-file ONNX GenAI package sequentially, reporting
/// aggregate byte progress, then returns the package directory.
final class OnnxGenAIFileSetDownloader: NSObject, ModelDownloading, @unchecked Sendable {
    private let destinationDirectory: URL
    private let files: [(name: String, repo: String)]
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
        destinationDirectory: URL = OnnxGenAIModelLocator.packageDirectory(),
        files: [(name: String, repo: String)] = OnnxGenAIModelLocator.files,
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

    // MARK: - Sequencing

    private func startNextFile() {
        let next: (name: String, repo: String)? = lock.withLock {
            guard index < files.count else { return nil }
            return files[index]
        }
        guard let next else {
            finish(.success(destinationDirectory))
            return
        }
        let destination = destinationDirectory.appendingPathComponent(next.name)
        if fileManager.fileExists(atPath: destination.path) {
            lock.withLock { completedBytes += Self.fileSize(destination) }
            advance()
            return
        }
        lock.withLock { currentDestination = destination }
        session.downloadTask(with: OnnxGenAIModelLocator.sourceURL(for: next.name, repo: next.repo)).resume()
    }

    private func advance() {
        lock.withLock { index += 1 }
        startNextFile()
    }

    private func report(received: Int64, expected: Int64) {
        let handler = lock.withLock { progressHandler }
        handler?(ModelDownloadProgress(receivedBytes: received, totalBytes: expected))
    }

    private static func fileSize(_ url: URL) -> Int64 {
        ((try? FileManager.default.attributesOfItem(atPath: url.path)[.size]) as? NSNumber)?.int64Value ?? 0
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

extension OnnxGenAIFileSetDownloader: URLSessionDownloadDelegate {
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
