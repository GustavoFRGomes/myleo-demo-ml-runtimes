import XCTest
@testable import MyLeoLite

final class WordpieceTokenizerTests: XCTestCase {
    // [PAD]=0 [UNK]=1 [CLS]=2 [SEP]=3 [MASK]=4 hello=5 world=6 ##s=7 ","=8 "!"=9
    private let vocab = ["[PAD]", "[UNK]", "[CLS]", "[SEP]", "[MASK]",
                         "hello", "world", "##s", ",", "!"]
    private var tokenizer: WordpieceTokenizer!

    override func setUp() {
        super.setUp()
        tokenizer = WordpieceTokenizer(vocab: vocab)
    }

    func test_basicTokenize_mixedCaseAndPunctuation_splitsCorrectly() {
        XCTAssertEqual(tokenizer.basicTokenize("Hello, World!"), ["hello", ",", "world", "!"])
    }

    func test_wordpieceTokenize_splitWord_returnsContinuationIds() {
        // "worlds" -> ["world", "##s"] -> [6, 7]
        XCTAssertEqual(tokenizer.wordpieceTokenize("worlds"), [6, 7])
    }

    func test_wordpieceTokenize_unknownWord_returnsUnkId() {
        XCTAssertEqual(tokenizer.wordpieceTokenize("qwertyuiop"), [1])
    }

    func test_wordpieceTokenize_tooLongWord_returnsUnkId() {
        XCTAssertEqual(tokenizer.wordpieceTokenize(String(repeating: "a", count: 101)), [1])
    }

    func test_tokenize_shortText_addsClsSepPadsAndMask() {
        let out = tokenizer.tokenize(text: "hello", maxSeqLen: 8)

        XCTAssertEqual(out.ids, [2, 5, 3, 0, 0, 0, 0, 0])
        XCTAssertEqual(out.mask, [1, 1, 1, 0, 0, 0, 0, 0])
        XCTAssertEqual(out.segmentIds, [0, 0, 0, 0, 0, 0, 0, 0])
    }

    func test_tokenize_longText_truncatesKeepingSep() {
        let out = tokenizer.tokenize(text: "hello world hello world hello", maxSeqLen: 6)

        XCTAssertEqual(out.ids.count, 6)
        XCTAssertEqual(out.ids.last, 3) // [SEP] kept
        XCTAssertEqual(out.mask, [1, 1, 1, 1, 1, 1])
    }

    func test_tokenize_missingSpecialToken_fallsBackToUnk() {
        // vocab has no [CLS]: [UNK]=0 [SEP]=1 [PAD]=2 hi=3
        let tok = WordpieceTokenizer(vocab: ["[UNK]", "[SEP]", "[PAD]", "hi"])
        let out = tok.tokenize(text: "hi", maxSeqLen: 4)

        XCTAssertEqual(out.ids, [0, 3, 1, 2])
    }
}
