import Foundation
import SwiftUI

/// Headless benchmark mode for device runs.
///
/// The app is launched with `--benchmark <id>` (see `BenchmarkID`); instead of
/// showing the chat UI it loads the model, runs one generation with the same
/// defaults as the UI (`GenerationParams.default`, 512 max tokens) and prints
/// machine-readable lines to stdout, which `devicectl device process launch
/// --console` streams back. It also writes a JSON report to `Documents/` and
/// terminates, so every launch is a cold process exactly like a UI run.
///
/// Nothing here runs unless the argument is present — normal app launches are
/// unaffected.
enum BenchmarkMode {
    static var requestedID: BenchmarkID? {
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: "--benchmark"), index + 1 < args.count {
            return BenchmarkID(rawValue: args[index + 1])
        }
        return args.first { $0.hasPrefix("--benchmark=") }
            .map { String($0.dropFirst("--benchmark=".count)) }
            .flatMap(BenchmarkID.init(rawValue:))
    }
}

enum BenchmarkID: String, CaseIterable {
    case litertGPU = "litert-gpu"
    case litertCPU = "litert-cpu"
    case coreml

    /// Stable label used in `docs/benchmarks.md`.
    var reportName: String {
        switch self {
        case .litertGPU: "litert-lm-gpu"
        case .litertCPU: "litert-lm-cpu"
        case .coreml: "coreml-ane"
        }
    }
}

/// Runs one benchmark generation and reports it. Mirrors `ChatStore`'s metrics
/// (`GenerationMetrics`: TTFT = submit → first non-empty chunk; tok/s = tokens
/// over the time after the first token) so the numbers match the UI.
@MainActor
final class BenchmarkRunner: ObservableObject {
    @Published private(set) var status = "Preparing…"

    private let prompt = "Explain how a refrigerator works in detail."
    private let maxTokens = 512

    func run(_ id: BenchmarkID) async {
        do {
            let engine = try makeEngine(id)
            status = "Loading model…"
            let info = try await engine.loadModel()
            emit("load", "\(info.loadMs)ms", extra: [
                "runtime": id.reportName,
                "backend": info.backend,
                "context": "\(info.maxContextTokens)",
                "sizeBytes": "\(info.sizeBytes)",
            ])

            status = "Generating…"
            let result = try await measure(engine)
            emit("gen", String(format: "TTFT %.0fms · %.1f tok/s · %d tokens", result.ttftMs, result.tokensPerSecond, result.tokens), extra: [
                "runtime": id.reportName,
                "ttftMs": "\(Int(result.ttftMs))",
                "totalMs": "\(Int(result.totalMs))",
                "tokens": "\(result.tokens)",
                "tokPerSec": String(format: "%.1f", result.tokensPerSecond),
                "replyChars": "\(result.replyChars)",
            ])
            writeReport(id: id, info: info, result: result)
            status = "Done"
            print("BENCHMARK|done|\(id.reportName)")
            try? await Task.sleep(nanoseconds: 200_000_000)
            exit(0)
        } catch {
            print("BENCHMARK|error|\(id.reportName)|\(error.localizedDescription)")
            status = "Failed: \(error.localizedDescription)"
            exit(1)
        }
    }

    // MARK: - Engines

    private func makeEngine(_ id: BenchmarkID) throws -> ChatInference {
        switch id {
        case .litertGPU, .litertCPU:
            guard let url = ModelLocator.locate() else {
                throw ChatError.modelNotFound("LiteRT-LM model not in Documents/Models")
            }
            return LiteRTLMEngine(modelURL: url, backend: id == .litertGPU ? .gpu : .cpu())
        case .coreml:
            guard let directory = MiniCPMModelLocator.locate() else {
                throw ChatError.modelNotFound("Core ML package not in Documents/Models")
            }
            return MiniCPMCoreMLEngine(packageDirectory: directory)
        }
    }

    // MARK: - Measurement

    private struct Result {
        let ttftMs: Double
        let totalMs: Double
        let tokens: Int
        let replyChars: Int

        var tokensPerSecond: Double {
            let decodeMs = totalMs - ttftMs
            guard decodeMs > 0, tokens > 0 else { return 0 }
            return Double(tokens) / (decodeMs / 1000)
        }
    }

    private func measure(_ engine: ChatInference) async throws -> Result {
        var params = GenerationParams.default
        params.maxTokens = maxTokens

        let started = Date()
        let tokensBefore = await engine.tokenCount()
        let stream = try await engine.generate(
            systemPrompt: "",
            messages: [ChatMessage(role: .user, text: prompt)],
            params: params
        )

        var firstChunkAt: Date?
        var reply = ""
        for try await chunk in stream where !chunk.isEmpty {
            if firstChunkAt == nil { firstChunkAt = Date() }
            reply += chunk
        }
        let done = Date()
        let tokensAfter = await engine.tokenCount()

        return Result(
            ttftMs: (firstChunkAt ?? done).timeIntervalSince(started) * 1000,
            totalMs: done.timeIntervalSince(started) * 1000,
            tokens: max(tokensAfter - tokensBefore, 0),
            replyChars: reply.count
        )
    }

    // MARK: - Reporting

    /// `BENCHMARK|<kind>|<value>|key=value|…` — one line, easy to grep from the
    /// `devicectl … --console` stream.
    private func emit(_ kind: String, _ value: String, extra: [String: String] = [:]) {
        let pairs = extra.sorted { $0.key < $1.key }.map { "\($0.key)=\($0.value)" }
        print((["BENCHMARK", kind, value] + pairs).joined(separator: "|"))
    }

    private func writeReport(id: BenchmarkID, info: ChatModelInfo, result: Result) {
        let payload: [String: Any] = [
            "runtime": id.reportName,
            "backend": info.backend,
            "model": info.displayName,
            "modelBytes": info.sizeBytes,
            "contextTokens": info.maxContextTokens,
            "loadMs": info.loadMs,
            "ttftMs": Int(result.ttftMs),
            "totalMs": Int(result.totalMs),
            "tokens": result.tokens,
            "tokPerSec": (result.tokensPerSecond * 10).rounded() / 10,
            "replyChars": result.replyChars,
            "prompt": prompt,
            "maxTokens": maxTokens,
            "temperature": GenerationParams.default.temperature,
            "topK": GenerationParams.default.topK,
            "topP": GenerationParams.default.topP,
            "device": ProcessInfo.processInfo.hostName,
            "osVersion": ProcessInfo.processInfo.operatingSystemVersionString,
            "timestamp": ISO8601DateFormatter().string(from: Date()),
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted, .sortedKeys]),
              let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return
        }
        let url = documents.appendingPathComponent("benchmark-\(id.reportName).json")
        try? data.write(to: url, options: .atomic)
    }
}

/// Minimal screen shown while a benchmark runs (also visible on screenshots).
struct BenchmarkScreen: View {
    let id: BenchmarkID
    @StateObject private var runner = BenchmarkRunner()

    var body: some View {
        VStack(spacing: 12) {
            Text("Benchmark · \(id.reportName)").font(.headline)
            Text(runner.status).font(.footnote).foregroundStyle(.secondary)
            ProgressView()
        }
        .padding()
        .task { await runner.run(id) }
    }
}
