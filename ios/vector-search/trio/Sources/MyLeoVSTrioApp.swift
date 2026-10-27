import SwiftUI

@main
struct MyLeoVSTrioApp: App {
    @StateObject private var session: RuntimeSession
    private let systemStats: IOSSystemStatsProvider

    init() {
        let stats = IOSSystemStatsProvider()
        let manager = RuntimeManager(documents: InMemoryDocumentRepository())
        let dataset = BundleEnronDatasetLoader()
        _session = StateObject(wrappedValue: RuntimeSession(
            manager: manager,
            systemStats: stats,
            datasetLoader: dataset
        ))
        self.systemStats = stats
    }

    var body: some Scene {
        WindowGroup {
            if EmbeddingBench.requested {
                ProgressView("Benchmarking embeddings…")
                    .task { await EmbeddingBench.run() }
            } else {
                AppRootView(
                    session: session,
                    systemStats: systemStats,
                    appLabel: "MyLeo Trio"
                )
            }
        }
    }
}
