import Combine
import SwiftUI
import UIKit

enum AppScreen: Equatable {
    case picker
    case search
    case ingest
    case detail(SearchHit)
}

private struct ShareItem: Identifiable {
    let id = UUID()
    let url: URL
    let mimeType: String
}

struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

struct AppRootView: View {
    @StateObject var session: RuntimeSession
    let systemStats: SystemStatsProvider
    let appLabel: String

    @State private var screen = AppScreen.picker
    @State private var showStats = false
    @State private var shareItem: ShareItem?
    @State private var pendingSwitch: InferenceRuntime?

    var body: some View {
        Group {
            switch screen {
            case .picker:
                RuntimePickerScreen(session: session)
                    .onReceive(session.$current.compactMap { $0 }) { _ in
                        screen = .search
                    }
            case .search:
                if let stores = session.currentStores, let runtime = session.current {
                    SearchScreen(
                        store: stores.search,
                        runtime: runtime,
                        onSwitchRuntime: { pendingSwitch = $0 },
                        onOpenIngest: { screen = .ingest },
                        onShowStats: { showStats = true },
                        onOpenHit: { screen = .detail($0) }
                    )
                } else {
                    RuntimePickerScreen(session: session)
                }
            case .ingest:
                if let stores = session.currentStores, let runtime = session.current {
                    IngestScreen(
                        store: stores.ingest,
                        runtime: runtime,
                        onBack: { screen = .search }
                    )
                } else {
                    RuntimePickerScreen(session: session)
                }
            case .detail(let hit):
                if let stores = session.currentStores {
                    ResultDetailScreen(
                        hit: hit,
                        query: stores.search.state.query,
                        onBack: { screen = .search }
                    )
                }
            }
        }
        .alert(item: $pendingSwitch) { target in
            Alert(
                title: Text("Switch to \(target.label)?"),
                message: Text("The index is shared between runtimes — switching clears it. The \(target.label) model stays loaded for instant switching back."),
                primaryButton: .destructive(Text("Switch & clear index")) {
                    session.select(target) { _ in screen = .search }
                },
                secondaryButton: .cancel()
            )
        }
        .sheet(isPresented: $showStats) {
            StatsSheetContent(data: reportData(), onExport: export)
        }
        .sheet(item: $shareItem) { item in
            ShareSheet(url: item.url)
        }
    }

    private func reportData() -> StatsReportData {
        guard let stores = session.currentStores else {
            return StatsReportData(
                device: systemStats.snapshot(),
                modelName: nil,
                modelUrl: nil,
                modelLoadMs: nil,
                modelDimensions: nil,
                chunkSize: nil,
                corpus: CorpusStats(),
                documents: [],
                lastRun: nil,
                lastQuery: nil
            )
        }
        let search = stores.search.state
        return StatsReportData(
            device: systemStats.snapshot(),
            modelName: stores.modelName,
            modelUrl: nil,
            modelLoadMs: stores.modelLoadMs,
            modelDimensions: InferenceRuntime.dimensions,
            chunkSize: nil,
            corpus: stores.ingest.state.corpus,
            documents: stores.ingest.state.documents,
            lastRun: stores.ingest.state.lastRun,
            lastQuery: search.queryMs.map { QueryStats(query: search.query, hits: search.hits.count, ms: $0) }
        )
    }

    private func export(_ format: StatsFormat) {
        let generatedAt = ISO8601DateFormatter().string(from: Date())
        let stamp = statsFileStamp()
        let fileName = "myleo-stats-\(stamp).\(format.fileExtension)"
        let content = format.render(appLabel: appLabel, data: reportData(), generatedAt: generatedAt)
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        do {
            let url = try StatsExporter(directory: directory).export(fileName: fileName, content: content)
            shareItem = ShareItem(url: url, mimeType: format.mimeType)
        } catch {
            // Sheet stays open; export errors are silent by design here —
            // production code would surface a toast/alert.
            print("stats export failed: \(error)")
        }
    }
}

private func statsFileStamp() -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyyMMdd-HHmmss"
    return formatter.string(from: Date())
}
