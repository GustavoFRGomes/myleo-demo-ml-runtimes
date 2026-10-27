import Foundation
import UIKit

/// Runs the 18-prompt suite on every available runtime and writes a Markdown + JSON report per
/// lane into `Documents/`.
///
/// ```
/// xcrun devicectl device process launch --console <udid> \
///   org.ggomes.myleo.decision.trio --suite-decision
/// ```
///
/// The app exits when both lanes are done; pull the reports with
/// `devicectl device copy from … --source Documents/julia-1-suite-*.md`.
@MainActor
enum SuiteBench {
    static var requested: Bool {
        ProcessInfo.processInfo.arguments.contains("--suite-decision")
    }

    static func run() async {
        UIDevice.current.isBatteryMonitoringEnabled = true
        let repeats = max(1, Int(ProcessInfo.processInfo.arguments
            .firstIndex(of: "--suite-repeats").map { $0 + 1 }
            .flatMap { $0 < ProcessInfo.processInfo.arguments.count ? ProcessInfo.processInfo.arguments[$0] : nil } ?? "") ?? 1)
        let only = ProcessInfo.processInfo.arguments
            .firstIndex(of: "--suite-runtime").map { $0 + 1 }
            .flatMap { $0 < ProcessInfo.processInfo.arguments.count ? ProcessInfo.processInfo.arguments[$0] : nil }
        let lanes: [DecisionRuntime] = only == "onnx" ? [.onnx] : only == "litert" ? [.litert] : [.litert, .onnx]
        for runtime in lanes {
            do {
                let engine = try await TrioBench.makeEngine(runtime)
                let info = try await engine.load()
                let started = Date()
                let batteryStart = UIDevice.current.batteryLevel
                let state = batteryState()
                var results: [SuiteResult] = []

                for _ in 1...repeats {
                    for item in DecisionSuite.cases {
                        let request = try DecisionRequest(state: item.state, question: item.question)
                        let outcome = try await engine.decide(request)
                        results.append(SuiteResult(item: item, outcome: outcome))
                    }
                }
                print("SUITE|runtime=\(runtime.rawValue)|prompts=\(results.count)|repeats=\(repeats)")

                let finished = Date()
                let report = SuiteReport(
                    runtimeLabel: runtime.label,
                    backend: info.accelerator.rawValue,
                    window: results.first?.outcome.window ?? 0,
                    device: UIDevice.current.model,
                    osVersion: UIDevice.current.systemVersion,
                    loadMs: info.totalLoadMs,
                    tokenizerLoadMs: info.tokenizerLoadMs,
                    graphLoadMs: info.graphLoadMs,
                    batteryStartPct: batteryStart < 0 ? -1 : Double(batteryStart) * 100,
                    batteryEndPct: batteryEndPercent(),
                    batteryState: state,
                    thermal: thermalState(),
                    startedAt: started,
                    finishedAt: finished,
                    results: results
                )
                try write(report)
                print(String(format: "SUITE|report|runtime=%@|avg=%.2f|min=%.2f|max=%.2f|wall=%.0f",
                             runtime.rawValue, report.averageMs, report.minMs, report.maxMs, report.wallMs))
            } catch {
                print("SUITE|runtime=\(runtime.rawValue)|error=\(error.localizedDescription)")
            }
        }
        exit(0)
    }

    private static func write(_ report: SuiteReport) throws {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let stamp = ISO8601DateFormatter().string(from: report.startedAt)
            .replacingOccurrences(of: ":", with: "")
        let base = documents.appendingPathComponent("\(report.fileStem)-\(stamp)")
        try report.markdown().write(to: base.appendingPathExtension("md"), atomically: true, encoding: .utf8)
        try report.json().write(to: base.appendingPathExtension("json"), atomically: true, encoding: .utf8)
        print("SUITE|wrote|\(base.path).md")
        print("SUITE|wrote|\(base.path).json")
    }

    private static func answer(_ outcome: DecisionOutcome) -> String {
        if let choice = outcome.choiceId { return choice }
        if let score = outcome.score { return String(format: "score %.3f", score) }
        if let noul = outcome.noul { return String(format: "P(true) %.3f", noul) }
        return "-"
    }

    private static func batteryEndPercent() -> Double {
        let level = UIDevice.current.batteryLevel
        return level < 0 ? -1 : Double(level) * 100
    }

    private static func batteryState() -> String {
        switch UIDevice.current.batteryState {
        case .charging: "charging"
        case .full: "full"
        case .unplugged: "unplugged"
        default: "unknown"
        }
    }

    private static func thermalState() -> String {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: "nominal"
        case .fair: "fair"
        case .serious: "serious"
        case .critical: "critical"
        @unknown default: "unknown"
        }
    }
}
