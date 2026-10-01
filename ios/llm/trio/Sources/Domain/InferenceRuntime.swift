import Foundation

/// The three on-device LLM runtimes this app can drive. LiteRT-LM and ONNX GenAI
/// share Phi-4-mini-instruct in each runtime's own format/precision; Core ML
/// uses MiniCPM5-1B (no Phi-4-mini Core ML export fits its hand-wired engine).
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
        case .litertLM: "Phi-4-mini-instruct q8 .litertlm · ~3.9 GB"
        case .onnxGenAI: "Phi-4-mini-instruct int4 ONNX GenAI · ~4.9 GB"
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
