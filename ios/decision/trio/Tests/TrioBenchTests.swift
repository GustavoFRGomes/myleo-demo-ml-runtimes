import XCTest
@testable import MyLeoDecisionTrio

final class TrioBenchTests: XCTestCase {
    func testCoreML_hasNoEngine() async {
        do {
            _ = try await TrioBench.makeEngine(.coreML)
            XCTFail("Core ML has no Julia-1 export; makeEngine must throw")
        } catch {
            XCTAssertTrue(error.localizedDescription.contains("Core ML"))
        }
    }

    func testDecisionRuntime_availabilityMatchesEngines() {
        XCTAssertTrue(DecisionRuntime.litert.isAvailable)
        XCTAssertTrue(DecisionRuntime.onnx.isAvailable)
        XCTAssertFalse(DecisionRuntime.coreML.isAvailable)
        XCTAssertNotNil(DecisionRuntime.coreML.unavailableReason)
    }
}
