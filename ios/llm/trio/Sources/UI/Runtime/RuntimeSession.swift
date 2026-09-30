import Combine
import Foundation

/// One runtime card on the picker.
struct RuntimeCard: Identifiable, Equatable {
    let runtime: InferenceRuntime
    var modelPresent: Bool

    var id: String { runtime.id }
}

/// Owns a cached `ChatStore` per runtime (so each keeps its own thread/models)
/// and the currently selected runtime. Mirrors the vector-search trio pattern,
/// minus the shared index (LLM threads are per-runtime and never shared).
@MainActor
final class RuntimeSession: ObservableObject {
    @Published private(set) var current: InferenceRuntime?
    @Published private(set) var cards: [RuntimeCard]

    private var stores: [InferenceRuntime: ChatStore] = [:]

    init() {
        cards = InferenceRuntime.allCases.map {
            RuntimeCard(runtime: $0, modelPresent: Self.hasModel($0))
        }
    }

    var currentStore: ChatStore? {
        current.map { store(for: $0) }
    }

    func store(for runtime: InferenceRuntime) -> ChatStore {
        if let existing = stores[runtime] { return existing }
        let store = Self.makeStore(runtime)
        stores[runtime] = store
        return store
    }

    func hasModel(_ runtime: InferenceRuntime) -> Bool {
        Self.hasModel(runtime)
    }

    func select(_ runtime: InferenceRuntime) {
        current = runtime
        _ = store(for: runtime)
        refreshCards()
    }

    func clearSelection() {
        current = nil
        refreshCards()
    }

    func refreshCards() {
        cards = InferenceRuntime.allCases.map {
            RuntimeCard(runtime: $0, modelPresent: Self.hasModel($0))
        }
    }

    // MARK: - Wiring

    static func hasModel(_ runtime: InferenceRuntime) -> Bool {
        switch runtime {
        case .litertLM: ModelLocator.locate() != nil
        case .onnxGenAI: OnnxGenAIModelLocator.locate() != nil
        case .coreML: MiniCPMModelLocator.locate() != nil
        }
    }

    static func makeStore(_ runtime: InferenceRuntime) -> ChatStore {
        switch runtime {
        case .litertLM:
            return ChatStore(
                locate: { ModelLocator.locate() },
                makeInference: { LiteRTLMEngine(modelURL: $0) },
                downloader: HuggingFaceModelDownloader(
                    destinationDirectory: ModelLocator.modelsDirectory()
                )
            )
        case .onnxGenAI:
            return ChatStore(
                locate: { OnnxGenAIModelLocator.locate() },
                makeInference: {
                    OnnxGenAIEngine(packageDirectory: $0, sizeBytes: OnnxGenAIModelLocator.packageSize())
                },
                downloader: OnnxGenAIFileSetDownloader()
            )
        case .coreML:
            return ChatStore(
                locate: { MiniCPMModelLocator.locate() },
                makeInference: {
                    MiniCPMCoreMLEngine(packageDirectory: $0, sizeBytes: MiniCPMModelLocator.packageSize())
                },
                downloader: MiniCPMFileSetDownloader()
            )
        }
    }
}
