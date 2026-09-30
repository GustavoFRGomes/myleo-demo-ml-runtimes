import XCTest
@testable import MyLeoLLMOnnxGenAI

final class GenerationParamsTests: XCTestCase {
    func test_validate_defaultParams_doesNotThrow() {
        XCTAssertNoThrow(try GenerationParams.default.validate())
    }

    func test_validate_negativeTemperature_throws() {
        var params = GenerationParams.default
        params.temperature = -0.1
        XCTAssertThrowsError(try params.validate()) {
            XCTAssertEqual($0 as? GenerationParams.ValidationError, .temperatureNegative)
        }
    }

    func test_validate_zeroTopK_throws() {
        var params = GenerationParams.default
        params.topK = 0
        XCTAssertThrowsError(try params.validate()) {
            XCTAssertEqual($0 as? GenerationParams.ValidationError, .topKTooSmall)
        }
    }

    func test_validate_topPOutOfRange_throws() {
        var params = GenerationParams.default
        params.topP = 1.5
        XCTAssertThrowsError(try params.validate()) {
            XCTAssertEqual($0 as? GenerationParams.ValidationError, .topPOutOfRange)
        }
    }

    func test_validate_zeroMaxTokens_throws() {
        var params = GenerationParams.default
        params.maxTokens = 0
        XCTAssertThrowsError(try params.validate()) {
            XCTAssertEqual($0 as? GenerationParams.ValidationError, .maxTokensTooSmall)
        }
    }

    func test_clamped_bringsEveryFieldIntoRange() {
        let clamped = GenerationParams(temperature: -5, topK: 0, topP: 9, maxTokens: 100_000).clamped()
        XCTAssertEqual(clamped.temperature, 0)
        XCTAssertEqual(clamped.topK, 1)
        XCTAssertEqual(clamped.topP, 1)
        XCTAssertEqual(clamped.maxTokens, 4096)
        XCTAssertNoThrow(try clamped.validate())
    }
}
