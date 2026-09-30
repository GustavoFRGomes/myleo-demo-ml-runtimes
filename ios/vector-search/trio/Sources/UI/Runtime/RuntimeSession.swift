import Combine
import Foundation

/// One runtime card on the picker.
struct RuntimeCard: Equatable, Identifiable {
    let runtime: InferenceRuntime
    var bundled: Bool
    var loaded: Bool
    var loadMs: Int64?

    var id: InferenceRuntime { runtime }
}

/// Per-runtime screen + inference stack. Cached so switching back keeps state.
struct RuntimeStores {
    let search: SearchStore
    let ingest: IngestStore
    let modelName: String
    let modelLoadMs: Int64
}

/// UI session over `RuntimeManager`: picker state, lazy selection, and one
/// cached `SearchStore`/`IngestStore` pair per runtime. Heavy model loading
/// runs off the main actor; all state publishes on it.
@MainActor
final class RuntimeSession: ObservableObject {
    @Published private(set) var current: InferenceRuntime?
    @Published private(set) var isLoading = false
    @Published private(set) var loadingRuntime: InferenceRuntime?
    @Published private(set) var error: String?
    @Published private(set) var cards: [RuntimeCard] = InferenceRuntime.allCases.map {
        RuntimeCard(runtime: $0, bundled: false, loaded: false, loadMs: nil)
    }

    private let manager: RuntimeManager
    private let systemStats: SystemStatsProvider
    private let datasetLoader: EmailDatasetLoader?
    private var storesCache: [InferenceRuntime: RuntimeStores] = [:]

    init(
        manager: RuntimeManager,
        systemStats: SystemStatsProvider,
        datasetLoader: EmailDatasetLoader? = nil
    ) {
        self.manager = manager
        self.systemStats = systemStats
        self.datasetLoader = datasetLoader
        Task { [weak self] in
            await self?.refreshCards()
        }
    }

    func stores(for runtime: InferenceRuntime) -> RuntimeStores? {
        storesCache[runtime]
    }

    var currentStores: RuntimeStores? {
        current.flatMap { storesCache[$0] }
    }

    /// Selects `runtime`, loading its model on first use. Calls `onReady` after
    /// stores are built. Heavy loading happens inside the manager, off the
    /// main actor; all state publishes on it.
    func select(_ runtime: InferenceRuntime, onReady: ((InferenceRuntime) -> Void)? = nil) {
        guard !isLoading else { return }
        isLoading = true
        loadingRuntime = runtime
        error = nil
        Task {
            do {
                let loaded = try await manager.select(runtime)
                didLoad(runtime: runtime, loaded: loaded, onReady: onReady)
            } catch {
                isLoading = false
                loadingRuntime = nil
                self.error = error.localizedDescription
                refreshCards()
            }
        }
    }

    func refreshCards() {
        cards = InferenceRuntime.allCases.map { runtime in
            let loaded = manager.current == runtime ? manager.currentLoaded() : nil
            return RuntimeCard(
                runtime: runtime,
                bundled: manager.hasBundledModel(runtime),
                loaded: manager.isLoaded(runtime),
                loadMs: loaded?.modelLoadMs
            )
        }
    }

    @MainActor
    private func didLoad(
        runtime: InferenceRuntime,
        loaded: LoadedRuntime,
        onReady: ((InferenceRuntime) -> Void)?
    ) {
        if storesCache[runtime] == nil {
            let useCase = CalculateEmbeddingUseCase(
                repository: DefaultEmbeddingRepository(embedder: loaded.embedder)
            )
            storesCache[runtime] = RuntimeStores(
                search: SearchStore(useCase: useCase, documents: manager.documents),
                ingest: IngestStore(
                    useCase: useCase,
                    documents: manager.documents,
                    modelLoadMs: loaded.modelLoadMs,
                    modelName: loaded.modelName,
                    modelUrl: nil,
                    modelDimensions: InferenceRuntime.dimensions,
                    chunkSize: nil,
                    systemStats: systemStats,
                    datasetLoader: datasetLoader
                ),
                modelName: loaded.modelName,
                modelLoadMs: loaded.modelLoadMs
            )
        }
        current = runtime
        isLoading = false
        loadingRuntime = nil
        refreshCards()
        onReady?(runtime)
    }
}
