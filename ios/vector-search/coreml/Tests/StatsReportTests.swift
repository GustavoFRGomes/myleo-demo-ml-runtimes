import XCTest
@testable import MyLeoVSCoreML

final class StatsReportTests: XCTestCase {
    private let data = StatsReportData(
        device: DeviceSnapshot(manufacturer: "Apple", model: "iPhone SE (3rd generation)", ramGB: 3.7, cpuCores: 6, gpuCores: 4, aneCores: 16, onBattery: true, batteryPercent: 62),
        modelName: "all-MiniLM-L6-v2",
        modelUrl: "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2",
        modelLoadMs: 812,
        modelDimensions: 384,
        chunkSize: 128,
        corpus: CorpusStats(documents: 2, chunks: 7),
        documents: [DocumentStat(label: "Document 1", chunks: 4), DocumentStat(label: "Document 2", chunks: 3)],
        lastRun: IngestRunStats(document: "Document 2", chunks: 3, embedMs: 285, storeMs: 13, totalMs: 301, batteryStartPct: 81, batteryEndPct: 79),
        lastQuery: QueryStats(query: "hello", hits: 5, ms: 42)
    )

    func test_buildStatsMarkdown_containsAllSections() {
        let md = buildStatsMarkdown(appLabel: "MyLeo VS Core ML", data: data, generatedAt: "2026-09-20T00:00:00Z")

        XCTAssertTrue(md.contains("# MyLeo VS Core ML — Vector Search Statistics"))
        XCTAssertTrue(md.contains("all-MiniLM-L6-v2"))
        XCTAssertTrue(md.contains("https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2"))
        XCTAssertTrue(md.contains("812 ms"))
        XCTAssertTrue(md.contains("Dimensions: 384"))
        XCTAssertTrue(md.contains("Chunk size: 128 tokens"))
        XCTAssertTrue(md.contains("Apple iPhone SE (3rd generation)"))
        XCTAssertTrue(md.contains("RAM: 3.7 GB · CPU: 6 · GPU: 4 · ANE: 16"))
        XCTAssertTrue(md.contains("on battery (62%)"))
        XCTAssertTrue(md.contains("Documents: 2, Chunks: 7"))
        XCTAssertTrue(md.contains("Document 1: 4 chunks"))
        XCTAssertTrue(md.contains("Document 2: 3 chunks in 301 ms (embed 285 ms, store 13 ms)"))
        XCTAssertTrue(md.contains("Battery: 81% → 79%"))
        XCTAssertTrue(md.contains("\"hello\": 5 hits in 42 ms"))
    }

    func test_buildStatsMarkdown_emptyState_showsPlaceholders() {
        let md = buildStatsMarkdown(
            appLabel: "MyLeo VS Core ML",
            data: StatsReportData(
                device: DeviceSnapshot(manufacturer: "unknown", model: "unknown", ramGB: nil, cpuCores: nil, gpuCores: nil, aneCores: nil, onBattery: true, batteryPercent: nil),
                modelName: nil, modelUrl: nil, modelLoadMs: nil, modelDimensions: nil, chunkSize: nil, corpus: CorpusStats(),
                documents: [], lastRun: nil, lastQuery: nil
            ),
            generatedAt: "2026-09-20T00:00:00Z"
        )

        XCTAssertTrue(md.contains("not bundled"))
        XCTAssertTrue(md.contains("none yet"))
        XCTAssertTrue(md.contains("Power: unknown"))
        XCTAssertTrue(md.contains("unknown RAM"))
    }

    func test_buildStatsJson_containsKeysAndValues() {
        let json = buildStatsJson(appLabel: "MyLeo VS Core ML", data: data, generatedAt: "2026-09-20T00:00:00Z")

        XCTAssertTrue(json.contains("\"app\": \"MyLeo VS Core ML\""))
        XCTAssertTrue(json.contains("\"name\": \"all-MiniLM-L6-v2\""))
        XCTAssertTrue(json.contains("\"url\": \"https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2\""))
        XCTAssertTrue(json.contains("\"dimensions\": 384"))
        XCTAssertTrue(json.contains("\"chunkSizeTokens\": 128"))
        XCTAssertTrue(json.contains("\"ramGB\": 3.7"))
        XCTAssertTrue(json.contains("\"cpuCores\": 6"))
        XCTAssertTrue(json.contains("\"gpuCores\": 4"))
        XCTAssertTrue(json.contains("\"aneCores\": 16"))
        XCTAssertTrue(json.contains("\"batteryPercent\": 62"))
        XCTAssertTrue(json.contains("\"documents\": 2"))
        XCTAssertTrue(json.contains("{\"label\": \"Document 1\", \"chunks\": 4}"))
        XCTAssertTrue(json.contains("\"batteryStartPct\": 81"))
        XCTAssertTrue(json.contains("\"query\": \"hello\""))
    }

    func test_buildStatsJson_escapesSpecialChars() {
        var data = self.data
        data = StatsReportData(
            device: data.device, modelName: data.modelName, modelUrl: data.modelUrl,
            modelLoadMs: data.modelLoadMs, modelDimensions: data.modelDimensions,
            chunkSize: data.chunkSize,
            corpus: data.corpus, documents: data.documents, lastRun: data.lastRun,
            lastQuery: QueryStats(query: "say \"hi\"\nnow", hits: 1, ms: 2)
        )
        let json = buildStatsJson(appLabel: "MyLeo VS Core ML", data: data, generatedAt: "2026-09-20T00:00:00Z")

        XCTAssertTrue(json.contains("\"query\": \"say \\\"hi\\\"\\nnow\""))
    }

    func test_marketingName_knownAndUnknown() {
        XCTAssertEqual(marketingName(for: "iPhone14,6"), "iPhone SE (3rd generation)")
        XCTAssertEqual(marketingName(for: "iPhone99,9"), "iPhone99,9")
    }

    func test_exporter_writesReadableFile() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        let url = try StatsExporter(directory: dir).export(fileName: "stats.md", content: "# hi")

        XCTAssertEqual(try String(contentsOf: url, encoding: .utf8), "# hi")
    }
}
