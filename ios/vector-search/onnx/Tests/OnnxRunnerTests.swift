import XCTest
@testable import MyLeoVSOnnx

final class OnnxRunnerTests: XCTestCase {
    func test_meanPool_averagesMaskedPositions() {
        // seq=3, dim=2; mask skips position 1
        let hidden: [Float] = [1, 0, 99, 99, 0, 4]

        XCTAssertEqual(meanPool(hidden: hidden, seqLen: 3, dim: 2, mask: [1, 0, 1]), [0.5, 2.0])
    }

    func test_meanPool_allMasked_returnsZeros() {
        XCTAssertEqual(meanPool(hidden: [5, 6, 7, 8], seqLen: 2, dim: 2, mask: [0, 0]), [0, 0])
    }

    func test_meanPool_singleToken_returnsIt() {
        XCTAssertEqual(meanPool(hidden: [3, 4], seqLen: 1, dim: 2, mask: [1]), [3, 4])
    }

    func test_factory_missingModel_throwsDescriptiveError() {
        let empty = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try? FileManager.default.createDirectory(at: empty, withIntermediateDirectories: true)
        guard let bundle = Bundle(url: empty) else {
            XCTFail("cannot build bundle from temp dir")
            return
        }

        XCTAssertThrowsError(try OnnxEmbedderFactory.create(bundle: bundle)) { error in
            XCTAssertTrue(
                (error as? LocalizedError)?.errorDescription?.contains("embedding.onnx") == true
            )
        }
    }
}
