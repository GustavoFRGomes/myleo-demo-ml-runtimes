import Foundation

/// The three on-device LLM runtimes this app can drive. Each has its own model
/// and format; `docs/llm-model-coverage.md` tracks which checkpoint runs where.
enum InferenceRuntime: String, CaseIterable, Identifiable, Sendable {
    case litertLM
    case onnxGenAI
    case coreML

    var id: String { rawValue }

    var label: String {
        switch self {
        case .litertLM: "LiteRT-LM"
        case .onnxGenAI: "ONNX GenAI"
        case .coreML: "Core ML"
        }
    }

    var subtitle: String {
        switch self {
        case .litertLM: "MiniCPM5-1B W4 .litertlm · ~793 MB"
        case .onnxGenAI: "MiniCPM5-1B int4 ONNX GenAI · ~1.4 GB"
        case .coreML: "MiniCPM5-1B 4-bit Core ML · 128-token context · ~619 MB"
        }
    }

    var backend: String {
        switch self {
        case .litertLM: "LiteRT-LM (GPU)"
        case .onnxGenAI: "ONNX Runtime GenAI (CPU)"
        case .coreML: "Core ML (ANE/GPU/CPU)"
        }
    }
}
