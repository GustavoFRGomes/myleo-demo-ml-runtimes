import XCTest
@testable import MyLeoLite

final class CSVParserTests: XCTestCase {
    func test_parseRows_quotedCommasAndEscapedQuotes() {
        let rows = CSVParser.parseRows("a,\"b,c\",\"d\"\"e\"\n1,2,3")

        XCTAssertEqual(rows, [["a", "b,c", "d\"e"], ["1", "2", "3"]])
    }

    func test_parseRows_embeddedNewlines_stayInField() {
        let rows = CSVParser.parseRows("h1,h2\n\"line1\nline2\",x\n")

        XCTAssertEqual(rows, [["h1", "h2"], ["line1\nline2", "x"]])
    }

    func test_parseRows_emptyFields_kept() {
        XCTAssertEqual(CSVParser.parseRows("a,,c\n"), [["a", "", "c"]])
    }

    func test_parseRows_trailingNewline_noPhantomRow() {
        XCTAssertEqual(CSVParser.parseRows("a,b\n"), [["a", "b"]])
    }

    func test_parseRows_carriageReturns_ignored() {
        XCTAssertEqual(CSVParser.parseRows("a,b\r\nc,d\r\n"), [["a", "b"], ["c", "d"]])
    }
}
