import XCTest
@testable import MyLeoLLMCoreML

@MainActor
final class RuntimeControllerTests: XCTestCase {
    private func freshDefaults() -> UserDefaults {
        UserDefaults(suiteName: "MyLeoLLMCoreMLTests.\(UUID().uuidString)")!
    }

    func test_defaultRuntime_isMiniCPM() {
        let controller = RuntimeController(persisted: freshDefaults())
        XCTAssertEqual(controller.runtime, .miniCPM)
        XCTAssertTrue(controller.runtime.downloadsModel)
    }

    func test_select_switchesRuntimeAndStore() {
        let controller = RuntimeController(persisted: freshDefaults())
        let original = controller.store

        controller.select(.appleFoundationModel)

        XCTAssertEqual(controller.runtime, .appleFoundationModel)
        XCTAssertFalse(controller.store === original)
        XCTAssertFalse(controller.runtime.downloadsModel)
    }

    func test_select_sameRuntime_keepsStore() {
        let controller = RuntimeController(persisted: freshDefaults())
        let original = controller.store

        controller.select(.miniCPM)

        XCTAssertTrue(controller.store === original)
    }

    func test_init_honoursPersistedRuntime() {
        let defaults = freshDefaults()
        defaults.set(ChatRuntime.appleFoundationModel.rawValue, forKey: "MyLeoLLMCoreML.runtime")
        let controller = RuntimeController(persisted: defaults)
        XCTAssertEqual(controller.runtime, .appleFoundationModel)
    }
}
