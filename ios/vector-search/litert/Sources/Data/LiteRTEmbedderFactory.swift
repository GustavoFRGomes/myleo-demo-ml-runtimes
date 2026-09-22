import Foundation
import TensorFlowLite

/// Builds the production embedding stack from the app bundle (model + vocab
/// bundled with the app — never needed in unit tests).
///
/// Expected bundle resources:
/// - `embedding.tflite` — BERT WordPiece embedding model
///   (Int32 `ids`/`mask`/`segment_ids` in, float embedding out).
/// - `vocab.txt` — one WordPiece token per line.
enum LiteRTEmbedderFactory {
    static let defaultModelName = "embedding"
    static let defaultVocabName = "vocab"
    static let defaultModelFileName = "embedding.tflite"

    static func create(
        bundle: Bundle = .main,
        modelName: String = defaultModelName,
        vocabName: String = defaultVocabName,
        maxSeqLen: Int = LiteRTEmbedder.defaultMaxSeqLen,
        threadCount: Int? = nil
    ) throws -> LiteRTEmbedder {
        guard let modelPath = bundle.path(forResource: modelName, ofType: "tflite") else {
            throw LiteRTEmbedderError.missingModel("\(modelName).tflite")
        }
        var options = Interpreter.Options()
        if let threadCount {
            options.threadCount = threadCount
        }
        let interpreter = try Interpreter(modelPath: modelPath, options: options)
        try interpreter.allocateTensors()
        guard let vocabPath = bundle.path(forResource: vocabName, ofType: "txt"),
              let vocabText = try? String(contentsOfFile: vocabPath, encoding: .utf8)
        else {
            throw LiteRTEmbedderError.missingVocab("\(vocabName).txt")
        }
        let vocab = vocabText.components(separatedBy: .newlines).filter { !$0.isEmpty }
        return LiteRTEmbedder(
            runner: InterpreterRunner(interpreter: interpreter),
            tokenizer: WordpieceTokenizer(vocab: vocab),
            maxSeqLen: maxSeqLen
        )
    }
}
