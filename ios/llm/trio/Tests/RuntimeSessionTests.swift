import XCTest
@testable import MyLeoLLMTrio

@MainActor
final class RuntimeSessionTests: XCTestCase {
    func test_initialState_hasNoSelectionAndACardPerRuntime() {
        let session = RuntimeSession()
        XCTAssertNil(session.current)
        XCTAssertEqual(session.cards.count, InferenceRuntime.allCases.count)
    }

    func test_select_setsCurrentAndCachesStorePerRuntime() {
        let session = RuntimeSession()

        session.select(.onnxGenAI)

        XCTAssertEqual(session.current, .onnxGenAI)
        XCTAssertTrue(session.store(for: .onnxGenAI) === session.store(for: .onnxGenAI))
        XCTAssertFalse(session.store(for: .onnxGenAI) === session.store(for: .coreML))
    }

    func test_clearSelection_returnsToPicker() {
        let session = RuntimeSession()
        session.select(.litertLM)

        session.clearSelection()

        XCTAssertNil(session.current)
    }

    func test_labelsAreDistinct() {
        XCTAssertEqual(Set(InferenceRuntime.allCases.map(\.label)).count, 3)
    }

    func test_hasModel_doesNotCrash() {
        let session = RuntimeSession()
        for runtime in InferenceRuntime.allCases {
            _ = session.hasModel(runtime)
        }
    }
}
