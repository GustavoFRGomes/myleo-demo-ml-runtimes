import XCTest
@testable import MyLeoVSCoreML

/// End-to-end embedding through the real bundled model. Runs on device only;
/// skips cleanly when the model files are not bundled (they never commit).
final class CoreMLEndToEndTests: XCTestCase {
    func test_embed_realModel_returnsNormalized384Vector() throws {
        // Unit tests run in the .xctest bundle; the model lives in the host
        // app bundle two levels up (<App>.app/PlugIns/<Tests>.xctest).
        let testURL = Bundle(for: CoreMLEndToEndTests.self).bundleURL
        let appURL = testURL.deletingLastPathComponent().deletingLastPathComponent()
        let candidates = [appURL]
            .compactMap { Bundle(url: $0) } + [Bundle.main]
        guard let bundle = candidates.first(where: {
            $0.url(forResource: "embedding", withExtension: "mlmodelc") != nil ||
                $0.url(forResource: "embedding", withExtension: "mlpackage") != nil
        }) else {
            throw XCTSkip("embedding model not bundled")
        }
        let embedder = try CoreMLEmbedderFactory.create(bundle: bundle)

        let vector = try embedder.embed(text: "Hello world")

        XCTAssertEqual(vector.count, 384)
        XCTAssertTrue(vector.allSatisfy { $0.isFinite })
        let norm = sqrt(vector.reduce(0) { $0 + $1 * $1 })
        XCTAssertEqual(norm, 1, accuracy: 1e-4)
    }
}
