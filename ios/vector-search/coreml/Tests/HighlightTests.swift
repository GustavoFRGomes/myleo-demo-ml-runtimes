import XCTest
@testable import MyLeoVSCoreML

final class HighlightTests: XCTestCase {
    func test_highlight_singleTerm_countsMatches() {
        let (_, count) = highlightMatches(in: "Hello hello world", query: "hello")

        XCTAssertEqual(count, 2)
    }

    func test_highlight_caseInsensitive() {
        let (_, count) = highlightMatches(in: "STATUS update", query: "status")

        XCTAssertEqual(count, 1)
    }

    func test_highlight_multipleTerms_countsAll() {
        let (_, count) = highlightMatches(in: "cats and dogs", query: "cats dogs")

        XCTAssertEqual(count, 2)
    }

    func test_highlight_noMatch_zeroCount() {
        let (text, count) = highlightMatches(in: "hello world", query: "quantum")

        XCTAssertEqual(count, 0)
        XCTAssertEqual(String(text.characters), "hello world")
    }

    func test_highlight_blankQuery_zeroCountUnchanged() {
        let (text, count) = highlightMatches(in: "hello", query: "   ")

        XCTAssertEqual(count, 0)
        XCTAssertEqual(String(text.characters), "hello")
    }

    func test_highlight_marksMatchedRuns() {
        let (text, _) = highlightMatches(in: "hi hello", query: "hello")
        let plain = String(text.characters)

        XCTAssertEqual(plain, "hi hello")
    }
}
