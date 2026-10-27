import Foundation

/// The three decision runtimes the app presents. Core ML is listed but not wired yet: there is
/// no Julia-1 Core ML export, so the card explains the gap instead of pretending to load.
enum DecisionRuntime: String, CaseIterable, Identifiable, Sendable {
    case litert
    case onnx
    case coreML

    var id: String { rawValue }

    var label: String {
        switch self {
        case .litert: "LiteRT"
        case .onnx: "ONNX Runtime"
        case .coreML: "Core ML"
        }
    }

    var subtitle: String {
        switch self {
        case .litert: "Julia-1 .tflite · GPU FP32 → CPU · host fp16 token table"
        case .onnx: "Julia-1 .onnx · CPU · weights + token table in-graph"
        case .coreML: "Julia-1 .mlpackage · not available yet"
        }
    }

    var isAvailable: Bool {
        switch self {
        case .litert, .onnx: true
        case .coreML: false
        }
    }

    var unavailableReason: String? {
        switch self {
        case .litert, .onnx: nil
        case .coreML: "No Julia-1 Core ML export exists yet. The runtime is reserved here so the picker stays stable when an .mlpackage lands."
        }
    }
}
