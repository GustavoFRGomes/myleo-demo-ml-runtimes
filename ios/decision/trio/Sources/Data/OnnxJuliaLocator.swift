import Foundation

struct OnnxRemoteFile: Sendable {
    let name: String
    let repo: String
    let path: String
    let bytes: Int64
}

struct OnnxJuliaFiles: Sendable {
    let directory: URL
    let model: URL
    let weights: URL
    let tokenizer: URL
    let tokenizerConfig: URL
}

/// `SupersonicLabs/Julia-1-ONNX` (revision `82a2fadf…`): graph + external weights + tokenizer.
/// The tokenizer config is not published in this repo; it comes from the base checkpoint.
enum OnnxJuliaLocator {
    static let folder = "julia-1-onnx"
    static let repo = "SupersonicLabs/Julia-1-ONNX"
    static let baseRepo = "SupersonicLabs/Julia-1"

    static let modelFile = "model.onnx"
    static let weightsFile = "model.onnx.data"
    static let tokenizerFile = "tokenizer.json"
    static let tokenizerConfigFile = "tokenizer_config.json"

    static let modelBytes: Int64 = 2_988_923
    static let weightsBytes: Int64 = 576_782_336
    static let tokenizerBytes: Int64 = 34_363_188
    static let tokenizerConfigBytes: Int64 = 652

    static let files: [OnnxRemoteFile] = [
        OnnxRemoteFile(name: modelFile, repo: repo, path: modelFile, bytes: modelBytes),
        OnnxRemoteFile(name: weightsFile, repo: repo, path: weightsFile, bytes: weightsBytes),
        OnnxRemoteFile(name: tokenizerFile, repo: repo, path: tokenizerFile, bytes: tokenizerBytes),
        OnnxRemoteFile(name: tokenizerConfigFile, repo: baseRepo, path: "tokenizer/tokenizer_config.json", bytes: tokenizerConfigBytes),
    ]

    static func directory(fileManager: FileManager = .default) -> URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Models", isDirectory: true)
            .appendingPathComponent(folder, isDirectory: true)
    }

    static func remoteURL(for file: OnnxRemoteFile) -> URL {
        URL(string: "https://huggingface.co/\(file.repo)/resolve/main/\(file.path)")!
    }

    static func locate(fileManager: FileManager = .default) -> OnnxJuliaFiles? {
        let dir = directory(fileManager: fileManager)
        for name in [modelFile, weightsFile, tokenizerFile, tokenizerConfigFile]
        where !fileManager.fileExists(atPath: dir.appendingPathComponent(name).path) {
            return nil
        }
        return OnnxJuliaFiles(
            directory: dir,
            model: dir.appendingPathComponent(modelFile),
            weights: dir.appendingPathComponent(weightsFile),
            tokenizer: dir.appendingPathComponent(tokenizerFile),
            tokenizerConfig: dir.appendingPathComponent(tokenizerConfigFile)
        )
    }

    static func tokenizerDirectory(fileManager: FileManager = .default) -> URL? {
        let dir = directory(fileManager: fileManager)
        for name in [tokenizerFile, tokenizerConfigFile]
        where !fileManager.fileExists(atPath: dir.appendingPathComponent(name).path) {
            return nil
        }
        return dir
    }
}
