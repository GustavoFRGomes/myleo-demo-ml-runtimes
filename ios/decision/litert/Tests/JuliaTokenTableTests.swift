import XCTest
@testable import MyLeoDecisionLiteRT

final class JuliaTokenTableTests: XCTestCase {
    private var url: URL!

    /// Writes a tiny little-endian float16 table: `vocabulary` rows × `width` values.
    private func makeTable(rows: [[Float]]) throws -> URL {
        let width = rows[0].count
        var data = Data()
        for row in rows {
            for value in row {
                var bits = Float16(value).bitPattern.littleEndian
                withUnsafeBytes(of: &bits) { data.append(contentsOf: $0) }
            }
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("julia-table-\(UUID().uuidString).bin")
        try data.write(to: url)
        XCTAssertEqual(data.count, rows.count * width * 2)
        return url
    }

    override func tearDownWithError() throws {
        if let url { try? FileManager.default.removeItem(at: url) }
    }

    func testGather_copiesRowsInOrder() throws {
        url = try makeTable(rows: [[1, 2], [3, 4], [5, 6]])
        let table = try JuliaTokenTable(fileURL: url, vocabularySize: 3, width: 2, padId: 0)
        var destination = [Float](repeating: 0, count: 2 * 2)

        try table.gather(ids: [2, 1], window: 2, into: &destination)

        XCTAssertEqual(destination, [5, 6, 3, 4])
    }

    func testGather_padsWithRealPadRow() throws {
        url = try makeTable(rows: [[9, 9], [1, 1]])
        let table = try JuliaTokenTable(fileURL: url, vocabularySize: 2, width: 2, padId: 0)
        var destination = [Float](repeating: 0, count: 3 * 2)

        try table.gather(ids: [1], window: 3, into: &destination)

        XCTAssertEqual(destination, [1, 1, 9, 9, 9, 9])
    }

    func testGather_rejectsWrongGeometryAndIds() throws {
        url = try makeTable(rows: [[1, 2], [3, 4]])
        let table = try JuliaTokenTable(fileURL: url, vocabularySize: 2, width: 2, padId: 0)
        var destination = [Float](repeating: 0, count: 6)

        XCTAssertThrowsError(try table.gather(ids: [0, 1], window: 1, into: &destination))
        XCTAssertThrowsError(try table.gather(ids: [2], window: 2, into: &destination))
        XCTAssertThrowsError(try table.gather(ids: [0], window: 4, into: &destination))
    }

    func testInit_rejectsTruncatedFile() throws {
        url = try makeTable(rows: [[1, 2]])
        XCTAssertThrowsError(try JuliaTokenTable(fileURL: url, vocabularySize: 2, width: 2, padId: 0))
    }
}
