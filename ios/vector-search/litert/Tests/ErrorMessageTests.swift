import XCTest
@testable import MyLeoLite

final class ErrorMessageTests: XCTestCase {
    func test_missingModelError_namesFile() {
        let message = LiteRTEmbedderError.missingModel("embedding.tflite").errorDescription

        XCTAssertTrue(message?.contains("embedding.tflite") == true)
        XCTAssertTrue(message?.contains("README") == true)
    }

    func test_missingVocabError_namesFile() {
        let message = LiteRTEmbedderError.missingVocab("vocab.txt").errorDescription

        XCTAssertTrue(message?.contains("vocab.txt") == true)
    }
}
