import Foundation
import SwiftUI

/// Highlights case-insensitive occurrences of the query's words in `text`.
/// Returns the attributed string plus the match count. Pure — unit-tested.
func highlightMatches(in text: String, query: String) -> (AttributedString, Int) {
    let terms = query.split(whereSeparator: \.isWhitespace).map(String.init).filter { !$0.isEmpty }
    guard !terms.isEmpty else { return (AttributedString(text), 0) }
    let pattern = terms.map(NSRegularExpression.escapedPattern(for:)).joined(separator: "|")
    guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else {
        return (AttributedString(text), 0)
    }
    let nsText = text as NSString
    let matches = regex.matches(in: text, range: NSRange(location: 0, length: nsText.length))
    var result = AttributedString()
    var cursor = text.startIndex
    for match in matches {
        guard let range = Range(match.range, in: text) else { continue }
        result += AttributedString(text[cursor..<range.lowerBound])
        var highlighted = AttributedString(text[range])
        highlighted.backgroundColor = .yellow
        result += highlighted
        cursor = range.upperBound
    }
    result += AttributedString(text[cursor...])
    return (result, matches.count)
}
