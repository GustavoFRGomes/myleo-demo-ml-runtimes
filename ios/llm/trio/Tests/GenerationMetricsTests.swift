import XCTest
@testable import MyLeoLLMTrio

final class GenerationMetricsTests: XCTestCase {
    func test_tokensPerSecond_usesDecodeWindowOnly() {
        // TTFT 200 ms, total 1200 ms → 1000 ms decode for 10 tokens = 10 tok/s.
        let metrics = GenerationMetrics(ttftMs: 200, totalMs: 1200, generatedTokens: 10)
        XCTAssertEqual(metrics.tokensPerSecond, 10.0, accuracy: 1e-9)
    }

    func test_tokensPerSecond_zeroTokens_isZero() {
        let metrics = GenerationMetrics(ttftMs: 100, totalMs: 500, generatedTokens: 0)
        XCTAssertEqual(metrics.tokensPerSecond, 0)
    }

    func test_tokensPerSecond_noDecodeWindow_isZero() {
        let metrics = GenerationMetrics(ttftMs: 500, totalMs: 500, generatedTokens: 3)
        XCTAssertEqual(metrics.tokensPerSecond, 0)
    }
}
