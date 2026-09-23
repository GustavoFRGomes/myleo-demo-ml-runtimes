import Foundation

enum CSVError: Error, Equatable {
    case emptyInput
}

/// Minimal RFC-4180 CSV reader: quoted fields, embedded commas/newlines and
/// `""` escapes. Pure functions — unit-tested with inline strings.
enum CSVParser {
    /// Parses all rows. Drops the single empty row a trailing newline implies.
    /// Iterates Unicode scalars so `\r\n` never fuses into one character.
    static func parseRows(_ text: String) -> [[String]] {
        var rows: [[String]] = []
        var current: [String] = []
        var field = ""
        var inQuotes = false
        var index = text.unicodeScalars.startIndex

        func advance() { index = text.unicodeScalars.index(after: index) }

        while index < text.unicodeScalars.endIndex {
            let scalar = text.unicodeScalars[index]
            if inQuotes {
                if scalar == "\"" {
                    let next = text.unicodeScalars.index(after: index)
                    if next < text.unicodeScalars.endIndex, text.unicodeScalars[next] == "\"" {
                        field.append("\"")
                        index = text.unicodeScalars.index(after: next)
                    } else {
                        inQuotes = false
                        advance()
                    }
                } else {
                    field.unicodeScalars.append(scalar)
                    advance()
                }
            } else if scalar == "\"" {
                inQuotes = true
                advance()
            } else if scalar == "," {
                current.append(field)
                field = ""
                advance()
            } else if scalar == "\n" {
                current.append(field)
                field = ""
                rows.append(current)
                current = []
                advance()
            } else if scalar == "\r" {
                advance() // paired with \n, handled above
            } else {
                field.unicodeScalars.append(scalar)
                advance()
            }
        }
        // Lenient close: an unterminated quote still yields its field.
        current.append(field)
        rows.append(current)
        return rows.filter { !($0.count == 1 && $0[0].isEmpty) }
    }

    /// Reads a CSV file and returns `(header, up to maxRows data rows)`.
    static func loadRows(from url: URL, maxRows: Int) throws -> (header: [String], rows: [[String]]) {
        let text: String
        do {
            text = try String(contentsOf: url, encoding: .utf8)
        } catch {
            throw DatasetError.missingFile(url.lastPathComponent)
        }
        var rows = parseRows(text)
        guard !rows.isEmpty else { throw CSVError.emptyInput }
        return (header: rows.removeFirst(), rows: Array(rows.prefix(maxRows)))
    }
}

enum DatasetError: Error, Equatable {
    case missingFile(String)
    case missingColumn(String)
}
