import Combine
import Foundation

/// Owns the selected chat runtime and the matching `ChatStore`. Switching
/// runtimes swaps the store (and resets the view via `.id(runtime)` in the App).
@MainActor
final class RuntimeController: ObservableObject {
    @Published private(set) var runtime: ChatRuntime
    @Published private(set) var store: ChatStore

    private static let defaultsKey = "MyLeoLLMCoreML.runtime"
    private let persisted: UserDefaults

    init(
        persisted: UserDefaults = .standard,
        initial: ChatRuntime? = nil
    ) {
        self.persisted = persisted
        let stored = persisted.string(forKey: Self.defaultsKey).flatMap(ChatRuntime.init(rawValue:))
        let resolved = initial ?? stored ?? .miniCPM
        self.runtime = resolved
        self.store = Self.makeStore(resolved)
    }

    func select(_ runtime: ChatRuntime) {
        guard runtime != self.runtime else { return }
        self.runtime = runtime
        persisted.set(runtime.rawValue, forKey: Self.defaultsKey)
        store = Self.makeStore(runtime)
    }

    static func makeStore(_ runtime: ChatRuntime) -> ChatStore {
        switch runtime {
        case .miniCPM:
            return ChatStore(
                locate: { MiniCPMModelLocator.locate() },
                makeInference: { url in
                    MiniCPMCoreMLEngine(
                        packageDirectory: url,
                        sizeBytes: MiniCPMModelLocator.packageSize()
                    )
                },
                downloader: MiniCPMFileSetDownloader()
            )
        case .appleFoundationModel:
            return ChatStore(
                locate: { SystemModelLocator.locate() },
                makeInference: { _ in
                    if #available(iOS 26.0, *) {
                        return FoundationModelsEngine()
                    } else {
                        return UnavailableInference()
                    }
                },
                downloader: DisabledModelDownloader(),
                downloadEnabled: false
            )
        }
    }
}
