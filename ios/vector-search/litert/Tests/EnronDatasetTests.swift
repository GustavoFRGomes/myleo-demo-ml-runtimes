import XCTest
@testable import MyLeoLite

final class EnronDatasetTests: XCTestCase {
    private let header = ["Folder-Name", "Date", "From", "To", "Subject", "Body", "Cc", "Bcc", "Label"]

    func test_rawString_joinsFieldsInOrderSkippingEmpties() {
        let email = EnronEmail(
            folderName: "arnold-j", date: "Thu, 16 Nov 2000", from: "a@x.com", to: "b@y.com",
            subject: "Status", body: "Hello\nworld", cc: "", bcc: ""
        )

        XCTAssertEqual(email.rawString(), "arnold-j Thu, 16 Nov 2000 a@x.com b@y.com Status Hello world")
    }

    func test_rawString_collapsesWhitespace() {
        let email = EnronEmail(
            folderName: "", date: "", from: "", to: "",
            subject: "  spaced   out  ", body: "line1\n\nline2", cc: "", bcc: ""
        )

        XCTAssertEqual(email.rawString(), "spaced out line1 line2")
    }

    func test_parseEmails_mapsColumnsByHeader() throws {
        let rows = [[
            "arnold-j", "Thu, 16 Nov 2000", "a@x.com", "b@y.com",
            "Status", "Body text", "", "", "0",
        ]]

        let emails = try EnronDatasetLoader.parseEmails(header: header, rows: rows)

        XCTAssertEqual(emails.count, 1)
        XCTAssertEqual(emails[0].folderName, "arnold-j")
        XCTAssertEqual(emails[0].subject, "Status")
        XCTAssertEqual(emails[0].body, "Body text")
    }

    func test_parseEmails_missingColumn_throws() {
        XCTAssertThrowsError(
            try EnronDatasetLoader.parseEmails(header: ["From"], rows: [["a@x.com"]])
        ) { error in
            XCTAssertEqual(error as? DatasetError, .missingColumn("Folder-Name"))
        }
    }

    func test_loadFirst_andCountRows_fromFile() throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".csv")
        try """
        Folder-Name,Date,From,To,Subject,Body,Cc,Bcc,Label
        f1,d1,a@x,b@y,s1,body one,,,"0"
        f2,d2,c@z,d@w,s2,"body, two",,,"1"
        """.write(to: url, atomically: true, encoding: .utf8)

        XCTAssertEqual(try EnronDatasetLoader.countRows(from: url), 2)
        let emails = try EnronDatasetLoader.loadFirst(2, from: url)
        XCTAssertEqual(emails.count, 2)
        XCTAssertEqual(emails[1].body, "body, two")
        XCTAssertEqual(emails[1].rawString(), "f2 d2 c@z d@w s2 body, two")
    }

    func test_loadFirst_missingFile_throws() {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("nope.csv")

        XCTAssertThrowsError(try EnronDatasetLoader.loadFirst(5, from: url)) { error in
            XCTAssertEqual(error as? DatasetError, .missingFile("nope.csv"))
        }
    }
}
