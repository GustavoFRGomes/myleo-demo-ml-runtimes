package org.ggomes.myleo.vectorsearch.dual.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.ggomes.myleo.vectorsearch.dual.data.CorpusStats
import org.ggomes.myleo.vectorsearch.dual.data.DocumentStat
import org.ggomes.myleo.vectorsearch.dual.domain.DeviceSnapshot
import org.ggomes.myleo.vectorsearch.dual.domain.QueryStats
import org.ggomes.myleo.vectorsearch.dual.domain.StatsReportData
import org.ggomes.myleo.vectorsearch.dual.ui.ingest.IngestRunStats
import org.ggomes.myleo.vectorsearch.dual.ui.theme.MyLeoVsDualTheme

/** Export formats offered by the stats sheet. */
enum class StatsFormat {
    MARKDOWN,
    JSON,
}

/** Bottom-sheet content: the same statistics that the .md/.json export carries. */
@Composable
fun StatsSheetContent(
    data: StatsReportData,
    onExport: (StatsFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Statistics", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Section("Embedding model")
        Line("File", data.modelName ?: "not bundled")
        Line("Loaded in", data.modelLoadMs?.let { "$it ms" } ?: "n/a")
        Section("Device")
        Line("Device", "${data.device.manufacturer} ${data.device.model}")
        Line("Power", powerText(data.device))
        Section("Index")
        Line("Totals", "${data.corpus.documents} documents · ${data.corpus.chunks} chunks")
        data.documents.forEach { Line(it.label, "${it.chunks} chunks") }
        Section("Last ingest run")
        val run = data.lastRun
        if (run == null) {
            Line("Run", "none yet")
        } else {
            Line(run.document, "${run.chunks} chunks in ${run.totalMs} ms (embed ${run.embedMs} ms, store ${run.storeMs} ms)")
            Line("Battery", "${pct(run.batteryStartPct)} → ${pct(run.batteryEndPct)}")
        }
        Section("Last search")
        val query = data.lastQuery
        if (query == null) {
            Line("Search", "none yet")
        } else {
            Line("\"${query.query}\"", "${query.hits} hits in ${query.ms} ms")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onExport(StatsFormat.MARKDOWN) }) { Text("Export .md") }
            OutlinedButton(onClick = { onExport(StatsFormat.JSON) }) { Text("Export JSON") }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Line(label: String, value: String) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
}

private fun powerText(device: DeviceSnapshot): String = when {
    device.batteryPercent == null -> "unknown"
    device.onBattery -> "on battery (${device.batteryPercent}%)"
    else -> "charging (${device.batteryPercent}%)"
}

private fun pct(value: Int?): String = value?.let { "$it%" } ?: "n/a"

@Preview(showBackground = true)
@Composable
fun StatsSheetPreview() {
    MyLeoVsDualTheme {
        StatsSheetContent(
            data = StatsReportData(
                device = DeviceSnapshot("Google", "Pixel 8", onBattery = true, batteryPercent = 62),
                modelName = "embedding.tflite",
                modelLoadMs = 812,
                corpus = CorpusStats(2, 7),
                documents = listOf(DocumentStat("Document 1", 4), DocumentStat("Document 2", 3)),
                lastRun = IngestRunStats("Document 2", 3, 285, 13, 301, 81, 79),
                lastQuery = QueryStats("hello", 5, 42),
            ),
            onExport = {},
        )
    }
}
