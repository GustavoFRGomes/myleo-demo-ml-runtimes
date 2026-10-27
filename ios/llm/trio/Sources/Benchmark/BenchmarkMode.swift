import Foundation
import SwiftUI
import UIKit

/// Headless benchmark mode for device runs.
///
/// The app is launched with `--benchmark <id>` (see `BenchmarkID`); instead of
/// showing the chat UI it loads the model, runs one or more generations with the
/// same defaults as the UI (`GenerationParams.default`, 512 max tokens) and
/// prints machine-readable lines to stdout, which `devicectl device process
/// launch --console` streams back. It also writes a JSON report to `Documents/`
/// and terminates, so every launch is a cold process exactly like a UI run.
///
/// Battery/energy support:
/// - `--bench-repeats N` runs the prompt N times, so the window is long enough to
///   move the battery gauge (iOS reports `batteryLevel` in coarse steps).
/// - `--bench-wait-unplugged` waits (up to 3 min) for the cable to come out before
///   starting; a charging phone cannot discharge, so measuring while plugged in is
///   meaningless.
///
/// Nothing here runs unless the argument is present — normal launches are
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
    case onnx = "onnx"
    case coreml

    /// Stable label used in `docs/benchmarks.md`.
    var reportName: String {
        switch self {
        case .litertGPU: "litert-lm-gpu"
        case .litertCPU: "litert-lm-cpu"
        case .onnx: "onnx-genai-cpu"
        case .coreml: "coreml-ane"
        }
    }
}

/// Extra switches that only matter for power runs.
struct BenchmarkOptions {
    var repeats = 1
    var waitUnplugged = false

    static var current: BenchmarkOptions {
        var options = BenchmarkOptions()
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: "--bench-repeats"), index + 1 < args.count,
           let repeats = Int(args[index + 1]) {
            options.repeats = max(1, repeats)
        }
        options.waitUnplugged = args.contains("--bench-wait-unplugged")
        return options
    }
}

/// Battery readings around a run. iOS quantises `batteryLevel` (roughly 5%),
/// so short runs read the same value before and after — that is a real limit of
/// the platform, not a bug here. Capacity/voltage are only used to turn a
/// percentage delta into an order-of-magnitude energy estimate.
struct PowerSampler {
    let capacityMAh: Double
    let nominalVolts: Double

    private(set) var startPct: Double = -1
    private(set) var endPct: Double = -1
    private(set) var startState: String = "unknown"
    private(set) var endState: String = "unknown"

    init(capacityMAh: Double = 2018, nominalVolts: Double = 3.86) {
        self.capacityMAh = capacityMAh
        self.nominalVolts = nominalVolts
    }

    mutating func start() {
        UIDevice.current.isBatteryMonitoringEnabled = true
        startPct = Self.level()
        startState = Self.state()
    }

    mutating func finish() {
        endPct = Self.level()
        endState = Self.state()
    }

    var deltaPct: Double {
        guard startPct >= 0, endPct >= 0 else { return 0 }
        return startPct - endPct
    }

    /// Rough pack energy for the observed delta. Zero when the gauge did not move.
    var energyMWh: Double { deltaPct / 100 * capacityMAh * nominalVolts }

    static func level() -> Double {
        let level = UIDevice.current.batteryLevel
        return level < 0 ? -1 : Double(level) * 100
    }

    static func state() -> String {
        switch UIDevice.current.batteryState {
        case .charging: "charging"
        case .full: "full"
        case .unplugged: "unplugged"
        default: "unknown"
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
            // A power run lasts minutes; let the screen auto-lock and iOS suspends
            // the app, which would stall the benchmark mid-generation.
            UIApplication.shared.isIdleTimerDisabled = true
            let options = BenchmarkOptions.current
            let engine = try makeEngine(id)
            status = "Loading model…"
            let info = try await engine.loadModel()
            emit("load", "\(info.loadMs)ms", extra: [
                "runtime": id.reportName,
                "backend": info.backend,
                "context": "\(info.maxContextTokens)",
                "sizeBytes": "\(info.sizeBytes)",
            ])

            var power = PowerSampler()
            power.start()
            if options.waitUnplugged {
                try await waitForUnplug()
                power = PowerSampler()
                power.start()
            }

            var runs: [Result] = []
            let started = Date()
            for index in 1...options.repeats {
                status = options.repeats == 1 ? "Generating…" : "Generating \(index)/\(options.repeats)…"
                let result = try await measure(engine)
                runs.append(result)
                emit("run", "\(index)", extra: [
                    "runtime": id.reportName,
                    "ttftMs": "\(Int(result.ttftMs))",
                    "totalMs": "\(Int(result.totalMs))",
                    "tokens": "\(result.tokens)",
                    "tokPerSec": String(format: "%.1f", result.tokensPerSecond),
                ])
            }
            let wall = Date().timeIntervalSince(started)
            power.finish()

            let tokens = runs.reduce(0) { $0 + $1.tokens }
            let avgTokPerSec = runs.isEmpty ? 0 : runs.map(\.tokensPerSecond).reduce(0, +) / Double(runs.count)
            let thermal = Self.thermal()
            emit("gen", String(format: "TTFT %.0fms · %.1f tok/s · %d tokens", runs.first?.ttftMs ?? 0, avgTokPerSec, tokens), extra: [
                "runtime": id.reportName,
                "runs": "\(runs.count)",
                "wallSec": String(format: "%.1f", wall),
                "tokPerSec": String(format: "%.1f", avgTokPerSec),
                "thermal": thermal,
            ])
            emit("power", String(format: "%.0f%% -> %.0f%%", power.startPct, power.endPct), extra: [
                "runtime": id.reportName,
                "deltaPct": String(format: "%.0f", power.deltaPct),
                "stateStart": power.startState,
                "stateEnd": power.endState,
                "energyMWh": String(format: "%.1f", power.energyMWh),
                "wallSec": String(format: "%.1f", wall),
            ])

            writeReport(id: id, info: info, runs: runs, power: power, wall: wall, thermal: thermal)
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

    /// Charging invalidates discharge measurement; wait for the cable to come out.
    private func waitForUnplug() async throws {
        UIDevice.current.isBatteryMonitoringEnabled = true
        status = "Unplug the cable to start…"
        let deadline = Date().addingTimeInterval(180)
        while UIDevice.current.batteryState != .unplugged, Date() < deadline {
            try? await Task.sleep(nanoseconds: 500_000_000)
        }
        guard UIDevice.current.batteryState == .unplugged else {
            throw ChatError.generationFailed("still on power after 180 s — unplug and relaunch")
        }
    }

    private static func thermal() -> String {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: "nominal"
        case .fair: "fair"
        case .serious: "serious"
        case .critical: "critical"
        @unknown default: "unknown"
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
        case .onnx:
            guard let directory = OnnxGenAIModelLocator.locate() else {
                throw ChatError.modelNotFound("ONNX GenAI package not in Documents/Models")
            }
            return OnnxGenAIEngine(
                packageDirectory: directory,
                sizeBytes: OnnxGenAIModelLocator.packageSize()
            )
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
            // Both engines reset their counter inside `generate` (new conversation /
            // prompt prefill), so a repeat can read `after == before`. Fall back to
            // the absolute count, which is that generation's prompt + output.
            tokens: max(tokensAfter - tokensBefore, tokensAfter > 0 ? tokensAfter : 0),
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

    private func writeReport(
        id: BenchmarkID,
        info: ChatModelInfo,
        runs: [Result],
        power: PowerSampler,
        wall: TimeInterval,
        thermal: String
    ) {
        let tokens = runs.reduce(0) { $0 + $1.tokens }
        let payload: [String: Any] = [
            "runtime": id.reportName,
            "backend": info.backend,
            "model": info.displayName,
            "modelBytes": info.sizeBytes,
            "contextTokens": info.maxContextTokens,
            "loadMs": info.loadMs,
            "repeats": runs.count,
            "ttftMs": Int(runs.first?.ttftMs ?? 0),
            "totalMs": Int(runs.first?.totalMs ?? 0),
            "tokens": tokens,
            "tokPerSec": (runs.isEmpty ? 0 : runs.map(\.tokensPerSecond).reduce(0, +) / Double(runs.count) * 10).rounded() / 10,
            "wallSec": (wall * 10).rounded() / 10,
            "thermalState": thermal,
            "batteryStartPct": power.startPct,
            "batteryEndPct": power.endPct,
            "batteryDeltaPct": power.deltaPct,
            "batteryStateStart": power.startState,
            "batteryStateEnd": power.endState,
            "energyMWhEstimate": (power.energyMWh * 10).rounded() / 10,
            "energyMjPerTokenEstimate": tokens > 0 && power.energyMWh > 0
                ? (power.energyMWh * 3.6 / Double(tokens) * 1000 * 10).rounded() / 10
                : 0,
            "prompt": prompt,
            "maxTokens": maxTokens,
            "temperature": GenerationParams.default.temperature,
            "topK": GenerationParams.default.topK,
            "topP": GenerationParams.default.topP,
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
