import Foundation

/// Read-only float16 token table from `litert-community/Julia-1-LiteRT`
/// (`julia1_token_table_fp16.bin`, `[256000, 384]`, little-endian). The graph takes
/// embeddings, not ids, so the host looks each token's row up and gathers fp32 rows into the
/// input buffer.
///
/// The file is mapped, not copied (196 MB). Positions past the ids gather the real `<pad>`
/// row (id 0), not a zero vector, exactly like the author's NumPy lookup.
///
/// Geometry is injectable so tests can use a tiny table; production uses the defaults.
final class JuliaTokenTable {
    static let vocabularySize = 256_000
    static let width = 384
    static let padId = 0
    static let sizeBytes = 196_608_000

    let vocabularySize: Int
    let width: Int
    let padId: Int

    private let data: Data

    init(
        fileURL: URL,
        vocabularySize: Int = JuliaTokenTable.vocabularySize,
        width: Int = JuliaTokenTable.width,
        padId: Int = JuliaTokenTable.padId
    ) throws {
        guard vocabularySize > 0, width > 0 else {
            throw DecisionError.model("table geometry must be positive")
        }
        guard (0..<vocabularySize).contains(padId) else {
            throw DecisionError.model("pad id out of range: \(padId)")
        }
        let expected = vocabularySize * width * 2
        let attributes = try? FileManager.default.attributesOfItem(atPath: fileURL.path)
        let actual = (attributes?[.size] as? NSNumber)?.intValue ?? -1
        guard actual == expected else {
            throw DecisionError.model("invalid token table size: \(actual) B; expected \(expected) B")
        }
        self.data = try Data(contentsOf: fileURL, options: [.mappedIfSafe])
        self.vocabularySize = vocabularySize
        self.width = width
        self.padId = padId
    }

    /// Fill row-major `[window, width]`: rows for `ids`, then `<pad>` rows up to `window`.
    @discardableResult
    func gather(ids: [Int], window: Int, into destination: inout [Float]) throws -> [Float] {
        guard window >= ids.count else {
            throw DecisionError.encoding("sequence of \(ids.count) ids exceeds window \(window)")
        }
        guard destination.count == window * width else {
            throw DecisionError.model("embedding destination must contain window * width values")
        }
        for id in ids where !(0..<vocabularySize).contains(id) {
            throw DecisionError.encoding("token id out of range: \(id)")
        }

        data.withUnsafeBytes { raw in
            let bytes = raw.bindMemory(to: UInt8.self)
            var out = 0
            for position in 0..<window {
                let token = position < ids.count ? ids[position] : padId
                var offset = token * width * 2
                for _ in 0..<width {
                    let bits = UInt16(bytes[offset]) | (UInt16(bytes[offset + 1]) << 8)
                    destination[out] = Float(Float16(bitPattern: bits))
                    out += 1
                    offset += 2
                }
            }
        }
        return destination
    }
}
