import Foundation

/// Resolves the `.litertlm` model file at runtime. The model is large
/// (~3.9 GB) and never bundled/committed, so the app looks in two places:
///
/// 1. the app bundle (only for small test models),
/// 2. `Documents/Models/` — where the user drops the downloaded file
///    (see the leaf README for the exact name + download link).
enum ModelLocator {
    /// Preferred Phi-4-mini-instruct variant: q8, ekv4096, ~3.9 GB.
    static let preferredFileName = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm"
    static let modelsSubdirectory = "Models"

    static func modelsDirectory(fileManager: FileManager = .default) -> URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(modelsSubdirectory, isDirectory: true)
    }

    /// Remote Hugging Face URL for a model file (shown in the info sheet).
    static func remoteURL(for fileName: String = preferredFileName) -> URL {
        HuggingFaceModelDownloader.repositoryBase.appendingPathComponent(fileName)
    }

    static func locate(bundle: Bundle = .main, fileManager: FileManager = .default) -> URL? {
        if let name = preferredFileName.split(separator: ".").first.map(String.init),
           let bundled = bundle.url(forResource: name, withExtension: "litertlm") {
            return bundled
        }
        if let anyBundled = bundle.urls(forResourcesWithExtension: "litertlm", subdirectory: nil)?.first {
            return anyBundled
        }

        let documents = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let modelsDir = documents.appendingPathComponent(modelsSubdirectory, isDirectory: true)
        let preferred = modelsDir.appendingPathComponent(preferredFileName)
        if fileManager.fileExists(atPath: preferred.path) {
            return preferred
        }
        let contents = (try? fileManager.contentsOfDirectory(
            at: modelsDir,
            includingPropertiesForKeys: nil
        )) ?? []
        return contents.first { $0.pathExtension == "litertlm" }
    }

    /// Human-readable instructions for the model-missing error state.
    static var missingModelHint: String {
        "Model not downloaded yet — use Download to fetch \(preferredFileName) (~3.9 GB) from Hugging Face, "
            + "or drop it into Documents/\(modelsSubdirectory)/ manually."
    }
}
