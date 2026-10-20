import XCTest
@testable import MyLeoLLMLiteRTLM

/// Pins the download contract: the exact bundle filename, the repository it comes
/// from, and the hint shown when it is missing. A typo in any of the three turns
/// into a silent 404 at runtime, which unit tests can catch here instead.
final class ModelLocatorTests: XCTestCase {
    func test_preferredFileName_isMiniCPM5GPUOptimizedBundle() {
        XCTAssertEqual(ModelLocator.preferredFileName, "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm")
    }

    func test_remoteURL_pointsAtTheMiniCPM5Repository() {
        XCTAssertEqual(
            ModelLocator.remoteURL().absoluteString,
            "https://huggingface.co/litert-community/MiniCPM5-1B/resolve/main/minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm"
        )
    }

    func test_modelsDirectory_livesUnderDocuments() {
        XCTAssertEqual(ModelLocator.modelsDirectory().lastPathComponent, ModelLocator.modelsSubdirectory)
        XCTAssertEqual(ModelLocator.modelsSubdirectory, "Models")
    }

    func test_missingModelHint_namesTheFileAndSize() {
        let hint = ModelLocator.missingModelHint

        XCTAssertTrue(hint.contains(ModelLocator.preferredFileName))
        XCTAssertTrue(hint.contains("793 MB"))
    }
}
