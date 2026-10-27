import Foundation

/// One file the app must fetch, with the size published by the model repo when known.
struct JuliaRemoteFile: Sendable {
    let name: String
    let repo: String
    let path: String
    let bytes: Int64?
}

/// The resolved on-disk package: tokenizer + token table + whichever graphs are installed.
struct JuliaModelFiles: Sendable {
    let directory: URL
    let tokenizer: URL
    let tokenizerConfig: URL
    let tokenTable: URL
    let graphs: [Int: URL]

    func graph(window: Int) -> URL? { graphs[window] }
    var windows: [Int] { graphs.keys.sorted() }
}

/// File names, sizes and Hugging Face delivery for `litert-community/Julia-1-LiteRT`
/// (revision `8b3733e196c201ee32e8f5063211588c7f87dd7e`). The package ships only
/// `tokenizer.json`; the small `tokenizer_config.json` comes from the base checkpoint because
/// swift-transformers loads it alongside the data file.
enum JuliaModelLocator {
    static let folder = "julia-1-litert"
    static let repo = "litert-community/Julia-1-LiteRT"
    static let tokenizerRepo = "SupersonicLabs/Julia-1"

    static let tokenizerFile = "tokenizer.json"
    static let tokenizerConfigFile = "tokenizer_config.json"
    static let tokenTableFile = "julia1_token_table_fp16.bin"
    static let graph512File = "julia1_s512_fp32.tflite"
    static let graph1024File = "julia1_s1024_fp32.tflite"

    static let tokenizerBytes: Int64 = 34_363_188
    static let tokenizerConfigBytes: Int64 = 652
    static let tokenTableBytes: Int64 = 196_608_000
    static let graph512Bytes: Int64 = 185_095_348
    static let graph1024Bytes: Int64 = 188_503_220

    /// The smallest usable package (512 graph + table + tokenizer files).
    static let required: [JuliaRemoteFile] = [
        JuliaRemoteFile(name: tokenizerFile, repo: repo, path: tokenizerFile, bytes: tokenizerBytes),
        JuliaRemoteFile(name: tokenizerConfigFile, repo: tokenizerRepo, path: "tokenizer/tokenizer_config.json", bytes: tokenizerConfigBytes),
        JuliaRemoteFile(name: tokenTableFile, repo: repo, path: tokenTableFile, bytes: tokenTableBytes),
        JuliaRemoteFile(name: graph512File, repo: repo, path: graph512File, bytes: graph512Bytes),
    ]

    static let optional: [JuliaRemoteFile] = [
        JuliaRemoteFile(name: graph1024File, repo: repo, path: graph1024File, bytes: graph1024Bytes),
    ]

    static func directory(fileManager: FileManager = .default) -> URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Models", isDirectory: true)
            .appendingPathComponent(folder, isDirectory: true)
    }

    static func remoteURL(for file: JuliaRemoteFile) -> URL {
        URL(string: "https://huggingface.co/\(file.repo)/resolve/main/\(file.path)")!
    }

    /// Directory when the tokenizer files are present (the bootstrap uses this before the
    /// 381 MB graph/table are installed).
    static func tokenizerDirectory(fileManager: FileManager = .default) -> URL? {
        let dir = directory(fileManager: fileManager)
        for name in [tokenizerFile, tokenizerConfigFile] {
            guard fileManager.fileExists(atPath: dir.appendingPathComponent(name).path) else { return nil }
        }
        return dir
    }

    /// Locates the package when the required files are present. Sizes are enforced by the
    /// downloader; this check only answers "is it complete enough to load".
    static func locate(fileManager: FileManager = .default) -> JuliaModelFiles? {
        let dir = directory(fileManager: fileManager)
        let requiredNames = required.map(\.name)
        for name in requiredNames where !fileManager.fileExists(atPath: dir.appendingPathComponent(name).path) {
            return nil
        }
        var graphs: [Int: URL] = [:]
        for (window, name) in [(512, graph512File), (1024, graph1024File)] {
            let url = dir.appendingPathComponent(name)
            if fileManager.fileExists(atPath: url.path) { graphs[window] = url }
        }
        return JuliaModelFiles(
            directory: dir,
            tokenizer: dir.appendingPathComponent(tokenizerFile),
            tokenizerConfig: dir.appendingPathComponent(tokenizerConfigFile),
            tokenTable: dir.appendingPathComponent(tokenTableFile),
            graphs: graphs
        )
    }
}
