import Foundation

/// The embedding runtime backing the shared index. Only one is active at a
/// time; switching clears the index. All three runtimes embed the same
/// MiniLM-L6-v2 weights (`.tflite` / `.onnx` / `.mlpackage`) into 384-d
/// vectors, so the shared index stays comparable — clearing only avoids
/// mixing sessions, not spaces.
enum InferenceRuntime: String, CaseIterable, Equatable, Hashable, Identifiable {
    case litert
    case onnx
    case coreml

    /// Identity for SwiftUI sheets/alerts.
    var id: String { rawValue }

    /// Short label for the picker and the search-screen switcher.
    var label: String {
        switch self {
        case .litert: "LiteRT"
        case .onnx: "ONNX"
        case .coreml: "Core ML"
        }
    }

    /// Model file name shown in the picker and the stats sheet.
    var modelFile: String {
        switch self {
        case .litert: "embedding.tflite"
        case .onnx: "embedding.onnx"
        case .coreml: "embedding.mlpackage"
        }
    }

    /// Native embedding width (identical for all three — same MiniLM weights).
    static let dimensions = 384

    /// One-line model description for the runtime picker.
    var description: String {
        switch self {
        case .litert: "MiniLM-L6-v2 as TFLite (384-d, ~90 MB)"
        case .onnx: "MiniLM-L6-v2 via ONNX Runtime (384-d, ~90 MB)"
        case .coreml: "MiniLM-L6-v2 as Core ML package (384-d, ~86 MB)"
        }
    }
}
