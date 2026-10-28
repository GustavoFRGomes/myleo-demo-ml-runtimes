import Foundation
import UIKit

/// Embedding benchmark for the vector-search trio, mirroring the decision/LLM harnesses.
///
/// ```
/// xcrun devicectl device process launch --console <udid> \
///   org.ggomes.myleo.vectorsearch.trio --bench-embedding
/// ```
///
/// Loads each bundled runtime, embeds a fixed 20-chunk corpus three times, and prints load and
/// per-chunk timings. Battery level is sampled around the run but, as documented, iOS cannot
/// resolve a workload this short; Power Profiler is the tool for energy.
@MainActor
enum EmbeddingBench {
    static var requested: Bool {
        ProcessInfo.processInfo.arguments.contains("--bench-embedding")
    }

    private static let corpus = [
        "The refrigerator keeps food cold by moving heat from inside the cabinet to the room.",
        "A compressor circulates refrigerant through the coils and the expansion valve drops its pressure.",
        "The thermostat cycles the compressor on and off to hold the set temperature.",
        "Freezers reach about minus eighteen degrees Celsius to preserve food for months.",
        "Quarterly revenue exceeded expectations after the new product launch.",
        "The sales team added three enterprise accounts in the northern region.",
        "A heat pump moves thermal energy in the opposite direction to a fridge.",
        "Insulation slows heat transfer so the cabinet stays cold with less work.",
        "The defrost cycle briefly warms the evaporator to melt accumulated frost.",
        "Server logs showed a spike in latency during the nightly backup window.",
        "The compressor draws more current at startup than while running steadily.",
        "A dirty condenser coil makes the fridge run longer and use more energy.",
        "The invoice lists a prorated charge for the partial month of service.",
        "Support replied within the hour and resolved the login problem.",
        "The parcel arrived two days late but the contents were undamaged.",
        "Our team migrated the analytics job to the new cluster last weekend.",
        "The battery drains quickly when the display brightness is at maximum.",
        "The contract renewal is due at the end of the next quarter.",
        "A refund was issued after the duplicate charge was confirmed.",
        "The model card documents the training data and known limitations.",
    ]

    static func run() async {
        UIApplication.shared.isIdleTimerDisabled = true
        UIDevice.current.isBatteryMonitoringEnabled = true
        let args = ProcessInfo.processInfo.arguments
        func value(_ flag: String) -> String? {
            guard let index = args.firstIndex(of: flag), index + 1 < args.count else { return nil }
            return args[index + 1]
        }
        let repeats = max(1, Int(value("--embed-repeats") ?? "") ?? 3)
        let only = value("--embed-runtime")
        let startLevel = UIDevice.current.batteryLevel
        let thermal = thermalState()

        for runtime in RuntimeKind.allCases where only == nil || only == runtime.rawValue {
            do {
                let loaded = try make(runtime)
                let loadMs = loaded.loadMs
                var times: [Double] = []
                for _ in 0..<repeats {
                    for chunk in corpus {
                        let started = Date()
                        _ = try loaded.embedder.embed(text: chunk)
                        times.append(Date().timeIntervalSince(started) * 1000)
                        // Long runs execute on the main actor; yield so the
                        // watchdog never sees a blocked runloop.
                        await Task.yield()
                    }
                }
                times.sort()
                let median = times[times.count / 2]
                let mean = times.reduce(0, +) / Double(times.count)
                let endLevel = UIDevice.current.batteryLevel
                print(String(format: "EMBED|runtime=%@|loadMs=%.1f|chunks=%d|medianMs=%.2f|meanMs=%.2f|minMs=%.2f|maxMs=%.2f|thermal=%@|battery=%.0f%%->%.0f%%|repeats=%d",
                             runtime.rawValue, loadMs, times.count, median, mean,
                             times.first ?? 0, times.last ?? 0, thermal,
                             startLevel < 0 ? -1 : Double(startLevel) * 100,
                             endLevel < 0 ? -1 : Double(endLevel) * 100, repeats))
                let report: [String: Any] = [
                    "runtime": runtime.rawValue,
                    "loadMs": loadMs,
                    "chunks": times.count,
                    "medianMs": median,
                    "meanMs": mean,
                    "minMs": times.first ?? 0,
                    "maxMs": times.last ?? 0,
                    "thermal": thermal,
                    "repeats": repeats,
                    "batteryStartPct": startLevel < 0 ? -1 : Int((Double(startLevel) * 100).rounded()),
                    "batteryEndPct": endLevel < 0 ? -1 : Int((Double(endLevel) * 100).rounded()),
                ]
                if let data = try? JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys]),
                   let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
                    try? data.write(to: documents.appendingPathComponent("bench-embedding-\(runtime.rawValue).json"))
                }
            } catch {
                print("EMBED|runtime=\(runtime.rawValue)|error=\(error.localizedDescription)")
            }
        }
        exit(0)
    }

    enum RuntimeKind: String, CaseIterable {
        case litert
        case onnx
        case coreml
    }

    private static func make(_ runtime: RuntimeKind) throws -> (embedder: Embedder, loadMs: Double) {
        let start = Date()
        let embedder: Embedder
        switch runtime {
        case .litert:
            // `--embed-cpu` skips the Core ML delegate: some devices crash the
            // process inside the delegate's warmup on this graph.
            let accelerator: LiteRTEmbedderFactory.Accelerator =
                ProcessInfo.processInfo.arguments.contains("--embed-cpu") ? .cpu : .coreml
            embedder = try LiteRTEmbedderFactory.create(accelerator: accelerator)
        case .onnx: embedder = try OnnxEmbedderFactory.create()
        case .coreml: embedder = try CoreMLEmbedderFactory.create()
        }
        return (embedder, Date().timeIntervalSince(start) * 1000)
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
