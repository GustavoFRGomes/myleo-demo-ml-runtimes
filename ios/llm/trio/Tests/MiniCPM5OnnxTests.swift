import XCTest
@testable import MyLeoLLMTrio

/// Pure-logic tests for the MiniCPM5 ONNX package preparation: the synthesized
/// `genai_config.json`, the tokenizer regex repair and the hand-built ChatML
/// prompt. No runtime and no model files are needed.
final class MiniCPM5OnnxTests: XCTestCase {
    private var directory: URL!

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("minicpm5-onnx-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: directory)
    }

    // MARK: - genai_config.json

    func testGenAIConfig_json_parsesAsJSON() throws {
        let data = try XCTUnwrap(MiniCPM5GenAIConfig.json.data(using: .utf8))
        let root = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        let model = try XCTUnwrap(root["model"] as? [String: Any])
        let decoder = try XCTUnwrap(model["decoder"] as? [String: Any])
        let search = try XCTUnwrap(root["search"] as? [String: Any])

        XCTAssertEqual(model["context_length"] as? Int, 131_072)
        XCTAssertEqual(model["vocab_size"] as? Int, 130_560)
        XCTAssertEqual(model["type"] as? String, "llama")
        XCTAssertEqual(decoder["filename"] as? String, "model.onnx")
        XCTAssertEqual(decoder["num_hidden_layers"] as? Int, 24)
        XCTAssertEqual(search["past_present_share_buffer"] as? Bool, true)
        XCTAssertNil(decoder["inputs"].flatMap { ($0 as? [String: Any])?["position_ids"] })
    }

    func testEnsure_missingFile_writesConfig() throws {
        let url = try MiniCPM5GenAIConfig.ensure(in: directory)

        XCTAssertEqual(url.lastPathComponent, "genai_config.json")
        XCTAssertEqual(try String(contentsOf: url, encoding: .utf8), MiniCPM5GenAIConfig.json)
    }

    func testEnsure_existingFile_isLeftUntouched() throws {
        let url = directory.appendingPathComponent(MiniCPM5GenAIConfig.fileName)
        try "{\"custom\":true}".write(to: url, atomically: true, encoding: .utf8)

        _ = try MiniCPM5GenAIConfig.ensure(in: directory)

        XCTAssertEqual(try String(contentsOf: url, encoding: .utf8), "{\"custom\":true}")
    }

    // MARK: - tokenizer repair

    func testTokenizerPatch_everyAnchor_isRewrittenOnce() throws {
        let url = directory.appendingPathComponent("tokenizer.json")
        try #"{"pattern":"\\p{N}+","again":"\\p{N}+"}"#.write(to: url, atomically: true, encoding: .utf8)

        let result = try TokenizerPatch.apply(to: url)

        XCTAssertEqual(result, .patched)
        let patched = try String(contentsOf: url, encoding: .utf8)
        XCTAssertEqual(patched, #"{"pattern":"\\p{N}{1,3}","again":"\\p{N}{1,3}"}"#)
    }

    func testTokenizerPatch_secondRun_isIdempotent() throws {
        let url = directory.appendingPathComponent("tokenizer.json")
        try #"{"pattern":"\\p{N}+"}"#.write(to: url, atomically: true, encoding: .utf8)
        XCTAssertEqual(try TokenizerPatch.apply(to: url), .patched)

        XCTAssertEqual(try TokenizerPatch.apply(to: url), .alreadyPatched)
    }

    func testTokenizerPatch_unrelatedFile_isNotModified() throws {
        let url = directory.appendingPathComponent("tokenizer.json")
        let original = #"{"pattern":"\p{L}+"}"#
        try original.write(to: url, atomically: true, encoding: .utf8)

        XCTAssertEqual(try TokenizerPatch.apply(to: url), .alreadyPatched)
        XCTAssertEqual(try String(contentsOf: url, encoding: .utf8), original)
    }

    // MARK: - chat template

    func testFormat_singleUserTurn_matchesTemplate() {
        let prompt = MiniCPM5ChatTemplate.format(
            systemPrompt: "",
            turns: [ChatMessage(role: .user, text: "Hello")]
        )
        XCTAssertEqual(prompt, "<s><|im_start|>user\nHello<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n")
    }

    func testFormat_systemAndHistory_keepsOrder() {
        let prompt = MiniCPM5ChatTemplate.format(
            systemPrompt: " Be brief. ",
            turns: [
                ChatMessage(role: .user, text: "Hi"),
                ChatMessage(role: .assistant, text: "Hello"),
                ChatMessage(role: .user, text: "And?"),
            ]
        )
        XCTAssertEqual(
            prompt,
            "<s><|im_start|>system\nBe brief.<|im_end|>\n"
                + "<|im_start|>user\nHi<|im_end|>\n"
                + "<|im_start|>assistant\nHello<|im_end|>\n"
                + "<|im_start|>user\nAnd?<|im_end|>\n"
                + "<|im_start|>assistant\n<think>\n\n</think>\n\n"
        )
    }

    // MARK: - locator

    func testLocator_packageIdentity_matchesAndroidArtifact() {
        XCTAssertEqual(OnnxGenAIModelLocator.folderName, "minicpm5-1b-onnx-int4")
        XCTAssertEqual(OnnxGenAIModelLocator.repo, "anydaytv/minicpm5-1b-onnx-int4")
        XCTAssertEqual(
            OnnxGenAIModelLocator.files.map(\.name),
            ["model.onnx", "model.onnx.data", "tokenizer.json", "tokenizer_config.json"]
        )
    }
}
