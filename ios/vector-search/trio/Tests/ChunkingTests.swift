import XCTest
@testable import MyLeoVSTrio

final class ChunkingTests: XCTestCase {
    func test_chunkCorpus_blankLineSeparatedParagraphs_splitsAndTrims() {
        XCTAssertEqual(chunkCorpus("  first  \n\nsecond\n\n\n  third\n"), ["first", "second", "third"])
    }

    func test_chunkCorpus_singleBlock_returnsOneChunk() {
        XCTAssertEqual(chunkCorpus("just one"), ["just one"])
    }

    func test_chunkCorpus_blankOrEmpty_returnsEmpty() {
        XCTAssertTrue(chunkCorpus("").isEmpty)
        XCTAssertTrue(chunkCorpus("  \n\n  ").isEmpty)
    }
}
