import SwiftUI

/// Bottom-sheet content: the same statistics that the .md/.json export carries.
struct StatsSheetContent: View {
    let data: StatsReportData
    var onExport: (StatsFormat) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                Text("Statistics").font(.title2)
                Group { section("Embedding model") }
                line("Model", data.modelName ?? "not bundled")
                line("URL", data.modelUrl ?? "n/a")
                line("Loaded in", data.modelLoadMs.map { "\($0) ms" } ?? "n/a")
                section("Device")
                line("Device", "\(data.device.manufacturer) \(data.device.model)")
                line("Specs", specsText(data.device))
                line("Power", powerText(data.device))
                section("Index")
                line("Totals", "\(data.corpus.documents) documents · \(data.corpus.chunks) chunks")
                ForEach(data.documents, id: \.label) { doc in
                    line(doc.label, "\(doc.chunks) chunks")
                }
                section("Last ingest run")
                if let run = data.lastRun {
                    line(run.document, "\(run.chunks) chunks in \(run.totalMs) ms (embed \(run.embedMs) ms, store \(run.storeMs) ms)")
                    line("Battery", "\(pct(run.batteryStartPct)) → \(pct(run.batteryEndPct))")
                } else {
                    line("Run", "none yet")
                }
                section("Last search")
                if let query = data.lastQuery {
                    line("\"\(query.query)\"", "\(query.hits) hits in \(query.ms) ms")
                } else {
                    line("Search", "none yet")
                }
                HStack(spacing: 8) {
                    Button("Export .md") { onExport(.markdown) }
                        .buttonStyle(.borderedProminent)
                    Button("Export JSON") { onExport(.json) }
                        .buttonStyle(.bordered)
                }
                .padding(.vertical, 8)
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func section(_ title: String) -> some View {
        Text(title).font(.headline).padding(.top, 8)
    }

    private func line(_ label: String, _ value: String) -> some View {
        Text("\(label): \(value)").font(.body)
    }

    private func powerText(_ device: DeviceSnapshot) -> String {
        guard let pct = device.batteryPercent else { return "unknown" }
        return device.onBattery ? "on battery (\(pct)%)" : "charging (\(pct)%)"
    }

    private func specsText(_ device: DeviceSnapshot) -> String {
        func text(_ value: CustomStringConvertible?) -> String {
            value.map(String.init(describing:)) ?? "unknown"
        }
        let ram = device.ramGB.map {
            String(format: "%.1f GB", locale: Locale(identifier: "en_US_POSIX"), $0)
        } ?? "unknown RAM"
        return "RAM: \(ram) · CPU: \(text(device.cpuCores)) · GPU: \(text(device.gpuCores)) · ANE: \(text(device.aneCores))"
    }

    private func pct(_ value: Int?) -> String {
        value.map { "\($0)%" } ?? "n/a"
    }
}

#Preview {
        StatsSheetContent(
            data: StatsReportData(
                device: DeviceSnapshot(manufacturer: "Apple", model: "iPhone SE (3rd generation)", ramGB: 3.7, cpuCores: 6, gpuCores: 4, aneCores: 16, onBattery: true, batteryPercent: 62),
                modelName: "all-MiniLM-L6-v2",
                modelUrl: "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2",
                modelLoadMs: 812,
            corpus: CorpusStats(documents: 2, chunks: 7),
            documents: [DocumentStat(label: "Document 1", chunks: 4), DocumentStat(label: "Document 2", chunks: 3)],
            lastRun: IngestRunStats(document: "Document 2", chunks: 3, embedMs: 285, storeMs: 13, totalMs: 301, batteryStartPct: 81, batteryEndPct: 79),
            lastQuery: QueryStats(query: "hello", hits: 5, ms: 42)
        ),
        onExport: { _ in }
    )
}
