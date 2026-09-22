import XCTest
@testable import MyLeoLite

final class StatsReportTests: XCTestCase {
    private let data = StatsReportData(
        device: DeviceSnapshot(manufacturer: "Apple", model: "iPhone16,2", onBattery: true, batteryPercent: 62),
        modelName: "embedding.tflite",
        modelLoadMs: 812,
        corpus: CorpusStats(documents: 2, chunks: 7),
        documents: [DocumentStat(label: "Document 1", chunks: 4), DocumentStat(label: "Document 2", chunks: 3)],
        lastRun: IngestRunStats(document: "Document 2", chunks: 3, embedMs: 285, storeMs: 13, totalMs: 301, batteryStartPct: 81, batteryEndPct: 79),
        lastQuery: QueryStats(query: "hello", hits: 5, ms: 42)
    )

    func test_buildStatsMarkdown_containsAllSections() {
        let md = buildStatsMarkdown(appLabel: "MyLeo LiteRT", data: data, generatedAt: "2026-09-20T00:00:00Z")

        XCTAssertTrue(md.contains("# MyLeo LiteRT — Vector Search Statistics"))
        XCTAssertTrue(md.contains("embedding.tflite"))
        XCTAssertTrue(md.contains("812 ms"))
        XCTAssertTrue(md.contains("Apple iPhone16,2"))
        XCTAssertTrue(md.contains("on battery (62%)"))
        XCTAssertTrue(md.contains("Documents: 2, Chunks: 7"))
        XCTAssertTrue(md.contains("Document 1: 4 chunks"))
        XCTAssertTrue(md.contains("Document 2: 3 chunks in 301 ms (embed 285 ms, store 13 ms)"))
        XCTAssertTrue(md.contains("Battery: 81% → 79%"))
        XCTAssertTrue(md.contains("\"hello\": 5 hits in 42 ms"))
    }

    func test_buildStatsMarkdown_emptyState_showsPlaceholders() {
        let md = buildStatsMarkdown(
            appLabel: "MyLeo LiteRT",
            data: StatsReportData(
                device: DeviceSnapshot(manufacturer: "unknown", model: "unknown", onBattery: true, batteryPercent: nil),
                modelName: nil, modelLoadMs: nil, corpus: CorpusStats(),
                documents: [], lastRun: nil, lastQuery: nil
            ),
            generatedAt: "2026-09-20T00:00:00Z"
        )

        XCTAssertTrue(md.contains("not bundled"))
        XCTAssertTrue(md.contains("none yet"))
        XCTAssertTrue(md.contains("Power: unknown"))
    }

    func test_buildStatsJson_containsKeysAndValues() {
        let json = buildStatsJson(appLabel: "MyLeo LiteRT", data: data, generatedAt: "2026-09-20T00:00:00Z")

        XCTAssertTrue(json.contains("\"app\": \"MyLeo LiteRT\""))
        XCTAssertTrue(json.contains("\"name\": \"embedding.tflite\""))
        XCTAssertTrue(json.contains("\"batteryPercent\": 62"))
        XCTAssertTrue(json.contains("\"documents\": 2"))
        XCTAssertTrue(json.contains("{\"label\": \"Document 1\", \"chunks\": 4}"))
        XCTAssertTrue(json.contains("\"batteryStartPct\": 81"))
        XCTAssertTrue(json.contains("\"query\": \"hello\""))
    }

    func test_buildStatsJson_escapesSpecialChars() {
        var data = self.data
        data = StatsReportData(
            device: data.device, modelName: data.modelName, modelLoadMs: data.modelLoadMs,
            corpus: data.corpus, documents: data.documents, lastRun: data.lastRun,
            lastQuery: QueryStats(query: "say \"hi\"\nnow", hits: 1, ms: 2)
        )
        let json = buildStatsJson(appLabel: "MyLeo LiteRT", data: data, generatedAt: "2026-09-20T00:00:00Z")

        XCTAssertTrue(json.contains("\"query\": \"say \\\"hi\\\"\\nnow\""))
    }

    func test_exporter_writesReadableFile() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let url = try StatsExporter(directory: dir).export(fileName: "stats.md", content: "# hi")

        XCTAssertEqual(try String(contentsOf: url, encoding: .utf8), "# hi")
    }
}
