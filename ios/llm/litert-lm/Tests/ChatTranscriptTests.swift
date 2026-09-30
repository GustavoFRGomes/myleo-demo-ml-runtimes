import XCTest
@testable import MyLeoLLMLiteRTLM

final class ChatTranscriptTests: XCTestCase {
    private func sampleData() -> TranscriptData {
        TranscriptData(
            modelName: "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm",
            modelBackend: "GPU",
            modelSizeBytes: 831_000_000,
            modelLoadMs: 1450,
            sourceURL: "https://huggingface.co/litert-community/MiniCPM5-1B/resolve/main/x.litertlm",
            systemPrompt: "You are terse.",
            params: GenerationParams(temperature: 0.8, topK: 40, topP: 0.95, maxTokens: 512),
            messages: [
                ChatMessage(role: .user, text: "Hello"),
                ChatMessage(
                    role: .assistant,
                    text: "Hi there",
                    metrics: GenerationMetrics(ttftMs: 100, totalMs: 1100, generatedTokens: 10)
                ),
                ChatMessage(role: .user, text: "More?"),
                ChatMessage(
                    role: .assistant,
                    text: "Sure",
                    metrics: GenerationMetrics(ttftMs: 200, totalMs: 700, generatedTokens: 5)
                ),
            ]
        )
    }

    func test_performance_summarizesAssistantMetrics() {
        let perf = TranscriptPerformance.from(sampleData().messages)

        XCTAssertEqual(perf.generations, 2)
        XCTAssertEqual(perf.totalTokens, 15)
        XCTAssertEqual(perf.totalDurationMs, 1800)
        XCTAssertEqual(perf.averageTTFTMs, 150, accuracy: 1e-9)
        XCTAssertEqual(perf.averageTokensPerSecond, 10.0, accuracy: 1e-9)
    }

    func test_performance_noGenerations_isEmpty() {
        XCTAssertEqual(TranscriptPerformance.from([ChatMessage(role: .user, text: "hi")]), .empty)
    }

    func test_markdown_containsSectionsAndPerMessageTiming() {
        let md = buildTranscriptMarkdown(appLabel: "MyLeo LLM LiteRT-LM", data: sampleData(), generatedAt: "2026-01-01")

        XCTAssertTrue(md.contains("# MyLeo LLM LiteRT-LM — Chat Transcript"))
        XCTAssertTrue(md.contains("## Model"))
        XCTAssertTrue(md.contains("minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm"))
        XCTAssertTrue(md.contains("## Generation parameters"))
        XCTAssertTrue(md.contains("## Performance"))
        XCTAssertTrue(md.contains("Average speed: 10.0 tok/s"))
        XCTAssertTrue(md.contains("### 2. Assistant"))
        XCTAssertTrue(md.contains("Hi there"))
        XCTAssertTrue(md.contains("- TTFT: 100 ms"))
        XCTAssertTrue(md.contains("You are terse."))
    }

    func test_json_isValidAndHasExpectedKeys() throws {
        let json = buildTranscriptJson(appLabel: "MyLeo LLM LiteRT-LM", data: sampleData(), generatedAt: "2026-01-01")
        let object = try JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any]

        XCTAssertEqual(object?["app"] as? String, "MyLeo LLM LiteRT-LM")
        let model = object?["model"] as? [String: Any]
        XCTAssertEqual(model?["backend"] as? String, "GPU")
        let perf = object?["performance"] as? [String: Any]
        XCTAssertEqual(perf?["totalTokens"] as? Int, 15)
        let messages = object?["messages"] as? [[String: Any]]
        XCTAssertEqual(messages?.count, 4)
        let assistantMetrics = messages?[1]["metrics"] as? [String: Any]
        XCTAssertEqual(assistantMetrics?["generatedTokens"] as? Int, 10)
    }

    func test_escapeJson_escapesSpecials() {
        XCTAssertEqual(escapeJson("a\"b\\c\nd\t"), "a\\\"b\\\\c\\nd\\t")
    }

    func test_exporter_writesReadableFile() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }

        let url = try TranscriptExporter(directory: directory)
            .export(fileName: "chat.md", content: "# hi")

        XCTAssertEqual(url.lastPathComponent, "chat.md")
        XCTAssertEqual(try String(contentsOf: url, encoding: .utf8), "# hi")
    }
}
