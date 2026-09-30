import XCTest
@testable import MyLeoLLMCoreML

final class MiniCPMSamplerTests: XCTestCase {
    private func params(temperature: Double = 0.8, topK: Int = 40, topP: Double = 0.95) -> GenerationParams {
        GenerationParams(temperature: temperature, topK: topK, topP: topP, maxTokens: 16)
    }

    func test_sample_lowTemperature_picksArgmax() {
        let logits: [Float] = [0.1, 5.0, 0.2, 0.3]
        let index = MiniCPMCoreMLEngine.sample(
            logits: logits,
            params: params(temperature: 0.0001),
            uniform: { 0.0 }
        )
        XCTAssertEqual(index, 1)
    }

    func test_sample_topKOne_alwaysPicksArgmax() {
        let logits: [Float] = [0.1, 0.2, 9.0, 0.3]
        let index = MiniCPMCoreMLEngine.sample(
            logits: logits,
            params: params(topK: 1),
            uniform: { 0.99 }
        )
        XCTAssertEqual(index, 2)
    }

    func test_sample_returnsValidIndex() {
        let logits = (0..<50).map { Float($0) / 10 }
        let index = MiniCPMCoreMLEngine.sample(logits: logits, params: params(), uniform: { 0.5 })
        XCTAssertTrue((0..<logits.count).contains(index))
    }

    func test_sample_emptyLogits_returnsZero() {
        XCTAssertEqual(MiniCPMCoreMLEngine.sample(logits: [], params: params()), 0)
    }
}
