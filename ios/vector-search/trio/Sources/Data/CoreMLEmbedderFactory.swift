import CoreML
import Foundation

/// Builds the production embedding stack from the app bundle (model + vocab
/// bundled with the app — never needed in unit tests).
///
/// Expected bundle resources:
/// - `embedding.mlpackage` — MiniLM sentence model converted with coremltools
///   (Int32 inputs, float `last_hidden_state` out; see leaf README).
/// - `vocab.txt` — one WordPiece token per line (same vocabulary as BERT).
enum CoreMLEmbedderFactory {
    static let defaultModelName = "embedding"
    static let defaultVocabName = "vocab"
    static let defaultModelFileName = "embedding.mlpackage"

    static func create(
        bundle: Bundle = .main,
        modelName: String = defaultModelName,
        vocabName: String = defaultVocabName,
        maxSeqLen: Int = LiteRTEmbedder.defaultMaxSeqLen
    ) throws -> LiteRTEmbedder {
        // Xcode compiles .mlpackage to .mlmodelc at build time and only ships
        // the compiled model — load that first, fall back to the source package.
        let modelURL = bundle.url(forResource: modelName, withExtension: "mlmodelc")
            ?? bundle.url(forResource: modelName, withExtension: "mlpackage")
        guard let modelURL else {
            throw CoreMLEmbedderError.missingModel("\(modelName).mlpackage")
        }
        let configuration = MLModelConfiguration()
        // ANE + CPU: embedding transformers run fastest on the Neural Engine;
        // expect a slower first load (ANE program compile) for faster embeds.
        configuration.computeUnits = .cpuAndNeuralEngine
        let model = try MLModel(contentsOf: modelURL, configuration: configuration)
        guard let vocabPath = bundle.path(forResource: vocabName, ofType: "txt"),
              let vocabText = try? String(contentsOfFile: vocabPath, encoding: .utf8)
        else {
            throw CoreMLEmbedderError.missingVocab("\(vocabName).txt")
        }
        let vocab = vocabText.components(separatedBy: .newlines).filter { !$0.isEmpty }
        return LiteRTEmbedder(
            runner: CoreMLRunner(model: model),
            tokenizer: WordpieceTokenizer(vocab: vocab),
            maxSeqLen: maxSeqLen
        )
    }
}
