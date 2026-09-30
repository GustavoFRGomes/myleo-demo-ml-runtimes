package org.ggomes.myleo.vectorsearch.onnx.domain

import android.content.Context

/** One email row of `enron_data_fraud_labeled.csv` (column subset we embed). */
data class EnronEmail(
    val folderName: String,
    val date: String,
    val from: String,
    val to: String,
    val subject: String,
    val body: String,
    val cc: String,
    val bcc: String,
) {
    /**
     * Single raw string fed into the text chunker: the eight fields in order,
     * space-separated, all whitespace runs collapsed.
     */
    fun rawString(): String =
        listOf(folderName, date, from, to, subject, body, cc, bcc)
            .flatMap { it.split(Regex("\\s+")).filter { part -> part.isNotEmpty() } }
            .joinToString(" ")
}

/** Maps header + rows to emails. Pure — unit-tested. */
object EnronDatasetLoader {
    val requiredColumns =
        listOf("Folder-Name", "Date", "From", "To", "Subject", "Body", "Cc", "Bcc")

    /** First [count] emails of CSV [text]. */
    fun loadFirst(count: Int, text: String): List<EnronEmail> {
        val (header, rows) = CsvParser.loadRows(text, count)
        return parseEmails(header, rows)
    }

    /** Number of data rows in CSV [text]. */
    fun countRows(text: String): Int {
        val (_, rows) = CsvParser.loadRows(text, Int.MAX_VALUE)
        return rows.size
    }

    fun parseEmails(header: List<String>, rows: List<List<String>>): List<EnronEmail> {
        val index = header.withIndex().associate { it.value to it.index }
        for (column in requiredColumns) {
            require(index.containsKey(column)) { "missing column $column" }
        }
        fun field(row: List<String>, column: String): String {
            val i = index.getValue(column)
            return if (i < row.size) row[i] else ""
        }
        return rows.map { row ->
            EnronEmail(
                folderName = field(row, "Folder-Name"),
                date = field(row, "Date"),
                from = field(row, "From"),
                to = field(row, "To"),
                subject = field(row, "Subject"),
                body = field(row, "Body"),
                cc = field(row, "Cc"),
                bcc = field(row, "Bcc"),
            )
        }
    }
}

/** Source of corpus emails. The asset loader is wired in production; fakes in tests. */
interface EmailDatasetLoader {
    fun availableEmails(): Int
    fun loadEmails(count: Int): List<String>
}

/**
 * [EmailDatasetLoader] reading the bundled `enron_sample.csv` from assets.
 * Missing asset surfaces as an exception at ingest time (never at startup).
 */
class AssetEnronDatasetLoader(
    private val context: Context,
    private val assetPath: String = DEFAULT_ASSET_PATH,
) : EmailDatasetLoader {
    override fun availableEmails(): Int =
        EnronDatasetLoader.countRows(readAsset())

    override fun loadEmails(count: Int): List<String> =
        EnronDatasetLoader.loadFirst(count, readAsset()).map { it.rawString() }

    private fun readAsset(): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }

    companion object {
        const val DEFAULT_ASSET_PATH = "enron_sample.csv"
    }
}
