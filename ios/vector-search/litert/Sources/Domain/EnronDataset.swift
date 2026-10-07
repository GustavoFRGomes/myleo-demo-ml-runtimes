import Foundation

/// One email row of `enron_data_fraud_labeled.csv` (column subset we embed).
struct EnronEmail: Equatable {
    let folderName: String
    let date: String
    let from: String
    let to: String
    let subject: String
    let body: String
    let cc: String
    let bcc: String

    /// Single raw string fed into the text chunker: the eight fields in order,
    /// space-separated, all whitespace runs collapsed.
    func rawString() -> String {
        [folderName, date, from, to, subject, body, cc, bcc]
            .flatMap { $0.split(whereSeparator: \.isWhitespace).map(String.init) }
            .joined(separator: " ")
    }
}

/// Loads emails from a bundled CSV sample of `enron_data_fraud_labeled.csv`.
struct EnronDatasetLoader {
    static let requiredColumns = ["Folder-Name", "Date", "From", "To", "Subject", "Body", "Cc", "Bcc"]

    /// First `count` emails of the CSV at `url`.
    static func loadFirst(_ count: Int, from url: URL) throws -> [EnronEmail] {
        let (header, rows) = try CSVParser.loadRows(from: url, maxRows: count)
        return try parseEmails(header: header, rows: rows)
    }

    /// Number of data rows in the CSV at `url`.
    static func countRows(from url: URL) throws -> Int {
        let (_, rows) = try CSVParser.loadRows(from: url, maxRows: .max)
        return rows.count
    }

    /// Maps header + rows to emails. Pure — unit-tested.
    static func parseEmails(header: [String], rows: [[String]]) throws -> [EnronEmail] {
        var index: [String: Int] = [:]
        for (i, name) in header.enumerated() { index[name] = i }
        for column in requiredColumns {
            guard index[column] != nil else { throw DatasetError.missingColumn(column) }
        }
        func field(_ row: [String], _ column: String) -> String {
            let i = index[column]!
            return i < row.count ? row[i] : ""
        }
        return rows.map { row in
            EnronEmail(
                folderName: field(row, "Folder-Name"),
                date: field(row, "Date"),
                from: field(row, "From"),
                to: field(row, "To"),
                subject: field(row, "Subject"),
                body: field(row, "Body"),
                cc: field(row, "Cc"),
                bcc: field(row, "Bcc")
            )
        }
    }
}

/// Source of corpus emails. The bundle loader is wired in production; fakes in tests.
protocol EmailDatasetLoader: Sendable {
    func availableEmails() throws -> Int
    func loadEmails(count: Int) throws -> [String]
}

/// `EmailDatasetLoader` reading the bundled `enron_sample.csv`.
/// Stateless apart from configuration, so `@unchecked Sendable` is safe.
struct BundleEnronDatasetLoader: EmailDatasetLoader, @unchecked Sendable {
    let bundle: Bundle
    let resourceName: String

    init(bundle: Bundle = .main, resourceName: String = "enron_sample") {
        self.bundle = bundle
        self.resourceName = resourceName
    }

    private func url() throws -> URL {
        guard let url = bundle.url(forResource: resourceName, withExtension: "csv") else {
            throw DatasetError.missingFile("\(resourceName).csv")
        }
        return url
    }

    func availableEmails() throws -> Int {
        try EnronDatasetLoader.countRows(from: url())
    }

    func loadEmails(count: Int) throws -> [String] {
        try EnronDatasetLoader.loadFirst(count, from: url()).map { $0.rawString() }
    }
}
