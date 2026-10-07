import Foundation

/// Splits raw corpus text into chunks (F-VS-1): paragraphs separated by blank
/// lines (whitespace-only lines count as blank), trimmed, empties dropped.
/// Same rules must apply in every variation.
func chunkCorpus(_ text: String) -> [String] {
    var chunks: [String] = []
    var current: [String] = []
    for line in text.components(separatedBy: .newlines) {
        if line.trimmingCharacters(in: .whitespaces).isEmpty {
            if !current.isEmpty {
                chunks.append(current.joined(separator: "\n"))
                current = []
            }
        } else {
            current.append(line)
        }
    }
    if !current.isEmpty {
        chunks.append(current.joined(separator: "\n"))
    }
    return chunks.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
}
