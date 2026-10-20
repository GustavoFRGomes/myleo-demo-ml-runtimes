import Foundation

/// Resolves the `.litertlm` model file at runtime. The model is large
/// (~793 MB) and never bundled/committed, so the app looks in two places:
///
/// 1. the app bundle (only for small test models),
/// 2. `Documents/Models/` — where the user drops the downloaded file
///    (see the leaf README for the exact name + download link).
enum ModelLocator {
    /// Preferred MiniCPM5-1B variant: mixed INT4-block32/INT8 W4, GPU-optimized, ~793 MB.
    /// Needs LiteRT-LM ≥ 0.18.0.
    static let preferredFileName = "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm"
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
        "Model not downloaded yet — use Download to fetch \(preferredFileName) (~793 MB) from Hugging Face, "
            + "or drop it into Documents/\(modelsSubdirectory)/ manually."
    }
}
