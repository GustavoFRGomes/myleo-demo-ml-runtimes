package org.ggomes.myleo.vectorsearch.dual.domain

import org.ggomes.myleo.vectorsearch.dual.data.CorpusStats
import org.ggomes.myleo.vectorsearch.dual.data.DocumentStat
import org.ggomes.myleo.vectorsearch.dual.ui.ingest.IngestRunStats

/** Last search summary for the report. */
data class QueryStats(
    val query: String,
    val hits: Int,
    val ms: Long,
)

/** Everything the statistics sheet/export shows. */
data class StatsReportData(
    val device: DeviceSnapshot,
    val modelName: String?,
    val modelLoadMs: Long?,
    val corpus: CorpusStats,
    val documents: List<DocumentStat>,
    val lastRun: IngestRunStats?,
    val lastQuery: QueryStats?,
)

/** Renders [data] as a Markdown report. Pure function — unit-tested. */
fun buildStatsMarkdown(appLabel: String, data: StatsReportData, generatedAt: String): String {
    val device = data.device
    val power = when {
        device.batteryPercent == null -> "unknown"
        device.onBattery -> "on battery (${device.batteryPercent}%)"
        else -> "charging (${device.batteryPercent}%)"
    }
    return buildString {
        appendLine("# $appLabel — Vector Search Statistics")
        appendLine()
        appendLine("_Generated: ${generatedAt}_")
        appendLine()
        appendLine("## Embedding model")
        appendLine()
        appendLine("- File: ${data.modelName ?: "not bundled"}")
        appendLine("- Loaded in: ${data.modelLoadMs?.let { "$it ms" } ?: "n/a"}")
        appendLine()
        appendLine("## Device")
        appendLine()
        appendLine("- ${device.manufacturer} ${device.model}")
        appendLine("- Power: $power")
        appendLine()
        appendLine("## Index")
        appendLine()
        appendLine("- Documents: ${data.corpus.documents}, Chunks: ${data.corpus.chunks}")
        data.documents.forEach { appendLine("- ${it.label}: ${it.chunks} chunks") }
        appendLine()
        appendLine("## Last ingest run")
        appendLine()
        val run = data.lastRun
        if (run == null) {
            appendLine("- none yet")
        } else {
            appendLine("- ${run.document}: ${run.chunks} chunks in ${run.totalMs} ms (embed ${run.embedMs} ms, store ${run.storeMs} ms)")
            appendLine("- Battery: ${pct(run.batteryStartPct)} → ${pct(run.batteryEndPct)}")
        }
        appendLine()
        appendLine("## Last search")
        appendLine()
        val query = data.lastQuery
        if (query == null) {
            appendLine("- none yet")
        } else {
            appendLine("- \"${query.query}\": ${query.hits} hits in ${query.ms} ms")
        }
    }
}

/** Renders [data] as a JSON report. Pure function — unit-tested. */
fun buildStatsJson(appLabel: String, data: StatsReportData, generatedAt: String): String {
    val device = data.device
    val run = data.lastRun
    val query = data.lastQuery
    return buildString {
        appendLine("{")
        appendLine("  \"app\": ${json(appLabel)},")
        appendLine("  \"generatedAt\": ${json(generatedAt)},")
        appendLine("  \"model\": {\"name\": ${jsonOrNull(data.modelName)}, \"loadMs\": ${data.modelLoadMs ?: "null"}},")
        appendLine("  \"device\": {\"manufacturer\": ${json(device.manufacturer)}, \"model\": ${json(device.model)}, \"onBattery\": ${device.onBattery}, \"batteryPercent\": ${device.batteryPercent ?: "null"}},")
        appendLine("  \"index\": {\"documents\": ${data.corpus.documents}, \"chunks\": ${data.corpus.chunks}, \"perDocument\": [${data.documents.joinToString(", ") { "{\"label\": ${json(it.label)}, \"chunks\": ${it.chunks}}" }}]},")
        if (run == null) {
            appendLine("  \"lastIngestRun\": null,")
        } else {
            appendLine("  \"lastIngestRun\": {\"document\": ${json(run.document)}, \"chunks\": ${run.chunks}, \"embedMs\": ${run.embedMs}, \"storeMs\": ${run.storeMs}, \"totalMs\": ${run.totalMs}, \"batteryStartPct\": ${run.batteryStartPct ?: "null"}, \"batteryEndPct\": ${run.batteryEndPct ?: "null"}},")
        }
        if (query == null) {
            appendLine("  \"lastSearch\": null")
        } else {
            appendLine("  \"lastSearch\": {\"query\": ${json(query.query)}, \"hits\": ${query.hits}, \"ms\": ${query.ms}}")
        }
        appendLine("}")
    }
}

private fun pct(value: Int?): String = value?.let { "$it%" } ?: "n/a"

private fun json(value: String): String = "\"${escapeJson(value)}\""

private fun jsonOrNull(value: String?): String = value?.let { json(it) } ?: "null"

internal fun escapeJson(value: String): String = buildString {
    for (c in value) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
