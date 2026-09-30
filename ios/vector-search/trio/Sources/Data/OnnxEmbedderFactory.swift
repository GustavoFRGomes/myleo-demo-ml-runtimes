import Foundation
import os

/// Builds the production embedding stack from the app bundle (model + vocab
/// bundled with the app — never needed in unit tests).
///
/// Expected bundle resources:
/// - `embedding.onnx` — MiniLM-style sentence model (Int64 `input_ids` /
///   `attention_mask` / `token_type_ids` in, float `last_hidden_state` out).
/// - `vocab.txt` — one WordPiece token per line (same vocabulary as BERT).
///
/// Acceleration: the Core ML execution provider is appended with an explicit
/// `MLComputeUnits = CPUAndNeuralEngine` request (ANE, never GPU), ML Program
/// format, and static shapes — the combination Core ML's scheduler places on
/// the Neural Engine most reliably. Core ML still decides per op: any op it
/// can't run on ANE falls back to CPU. Only Xcode's Core ML instrument proves
/// actual placement; this code proves the request + provider list.
enum OnnxEmbedderFactory {
    static let defaultModelName = "embedding"
    static let defaultVocabName = "vocab"
    static let defaultModelFileName = "embedding.onnx"

    /// Set ORT's log level to `.verbose` to print per-node provider placement
    /// ("Node(s) placed on [CoreMLExecutionProvider]") in device logs.
    static let ortLoggingLevel: ORTLoggingLevel = .warning

    private static let log = Logger(
        subsystem: "org.ggomes.myleo.vectorsearch.trio",
        category: "ONNX"
    )

    static func create(
        bundle: Bundle = .main,
        modelName: String = defaultModelName,
        vocabName: String = defaultVocabName,
        maxSeqLen: Int = LiteRTEmbedder.defaultMaxSeqLen
    ) throws -> LiteRTEmbedder {
        guard let modelPath = bundle.path(forResource: modelName, ofType: "onnx") else {
            throw OnnxEmbedderError.missingModel("\(modelName).onnx")
        }
        let env = try ORTEnv(loggingLevel: ortLoggingLevel)
        let sessionOptions = try ORTSessionOptions()
        appendCoreMLProviderIfAvailable(sessionOptions, modelName: modelName)
        let session = try ORTSession(env: env, modelPath: modelPath, sessionOptions: sessionOptions)
        guard let vocabPath = bundle.path(forResource: vocabName, ofType: "txt"),
              let vocabText = try? String(contentsOfFile: vocabPath, encoding: .utf8)
        else {
            throw OnnxEmbedderError.missingVocab("\(vocabName).txt")
        }
        let vocab = vocabText.components(separatedBy: .newlines).filter { !$0.isEmpty }
        return LiteRTEmbedder(
            runner: OnnxRunner(session: session),
            tokenizer: WordpieceTokenizer(vocab: vocab),
            maxSeqLen: maxSeqLen
        )
    }

    /// Appends the Core ML EP, explicitly requesting the Neural Engine.
    /// Falls back to a plain CPU session only when the EP is unavailable or
    /// the options are rejected (not silently).
    private static func appendCoreMLProviderIfAvailable(
        _ sessionOptions: ORTSessionOptions,
        modelName: String
    ) {
        guard ORTIsCoreMLExecutionProviderAvailable() else {
            log.info("Core ML EP unavailable on this device; CPU session")
            return
        }
        // V2 options: explicit compute units + ML Program + static shapes.
        // CPUAndNeuralEngine excludes GPU — we want ANE, not Metal.
        let providerOptions: [String: String] = [
            "MLComputeUnits": "CPUAndNeuralEngine",
            "ModelFormat": "MLProgram",
            "RequireStaticInputShapes": "1",
            "EnableOnSubgraphs": "1",
        ]
        do {
            try sessionOptions.appendCoreMLExecutionProvider(withOptionsV2: providerOptions)
            log.info("Core ML EP appended for \(modelName).onnx (MLComputeUnits=CPUAndNeuralEngine)")
        } catch {
            log.info("Core ML EP V2 rejected (\(error.localizedDescription)); trying legacy options")
            let legacy = ORTCoreMLExecutionProviderOptions()
            legacy.enableOnSubgraphs = true
            legacy.useCPUOnly = false
            legacy.useCPUAndGPU = false
            legacy.onlyEnableForDevicesWithANE = true
            do {
                try sessionOptions.appendCoreMLExecutionProvider(with: legacy)
                log.info("Core ML EP appended for \(modelName).onnx (ANE-preferred legacy options)")
            } catch {
                log.info("Core ML EP rejected (\(error.localizedDescription)); CPU session")
            }
        }
    }
}
