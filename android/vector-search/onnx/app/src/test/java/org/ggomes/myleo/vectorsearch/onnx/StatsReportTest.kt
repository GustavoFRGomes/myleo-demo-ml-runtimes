package org.ggomes.myleo.vectorsearch.onnx

import org.ggomes.myleo.vectorsearch.onnx.data.CorpusStats
import org.ggomes.myleo.vectorsearch.onnx.data.DocumentStat
import org.ggomes.myleo.vectorsearch.onnx.domain.DeviceSnapshot
import org.ggomes.myleo.vectorsearch.onnx.domain.QueryStats
import org.ggomes.myleo.vectorsearch.onnx.domain.StatsReportData
import org.ggomes.myleo.vectorsearch.onnx.domain.buildStatsJson
import org.ggomes.myleo.vectorsearch.onnx.domain.buildStatsMarkdown
import org.ggomes.myleo.vectorsearch.onnx.ui.ingest.IngestRunStats
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class StatsReportTest {

    private val data = StatsReportData(
        device = DeviceSnapshot("Google", "Pixel 8", onBattery = true, batteryPercent = 62),
        modelName = "embedding.tflite",
        modelLoadMs = 812,
        corpus = CorpusStats(2, 7),
        documents = listOf(DocumentStat("Document 1", 4), DocumentStat("Document 2", 3)),
        lastRun = IngestRunStats("Document 2", 3, 285, 13, 301, 81, 79),
        lastQuery = QueryStats("hello", 5, 42),
    )

    @Test
    fun buildStatsMarkdown_containsAllSections() {
        val md = buildStatsMarkdown("MyLeo VS ONNX", data, "2026-09-20T00:00:00Z")

        assertThat(md).contains("# MyLeo VS ONNX — Vector Search Statistics")
        assertThat(md).contains("embedding.tflite")
        assertThat(md).contains("812 ms")
        assertThat(md).contains("Google Pixel 8")
        assertThat(md).contains("on battery (62%)")
        assertThat(md).contains("Documents: 2, Chunks: 7")
        assertThat(md).contains("Document 1: 4 chunks")
        assertThat(md).contains("Document 2: 3 chunks in 301 ms (embed 285 ms, store 13 ms)")
        assertThat(md).contains("Battery: 81% → 79%")
        assertThat(md).contains("\"hello\": 5 hits in 42 ms")
    }

    @Test
    fun buildStatsMarkdown_emptyState_showsPlaceholders() {
        val md = buildStatsMarkdown(
            "MyLeo VS ONNX",
            StatsReportData(
                device = DeviceSnapshot("unknown", "unknown", onBattery = true, batteryPercent = null),
                modelName = null,
                modelLoadMs = null,
                corpus = CorpusStats(),
                documents = emptyList(),
                lastRun = null,
                lastQuery = null,
            ),
            "2026-09-20T00:00:00Z",
        )

        assertThat(md).contains("not bundled")
        assertThat(md).contains("none yet")
        assertThat(md).contains("Power: unknown")
    }

    @Test
    fun buildStatsJson_containsKeysAndValues() {
        val json = buildStatsJson("MyLeo VS ONNX", data, "2026-09-20T00:00:00Z")

        assertThat(json).contains("\"app\": \"MyLeo VS ONNX\"")
        assertThat(json).contains("\"name\": \"embedding.tflite\"")
        assertThat(json).contains("\"loadMs\": 812")
        assertThat(json).contains("\"manufacturer\": \"Google\"")
        assertThat(json).contains("\"batteryPercent\": 62")
        assertThat(json).contains("\"documents\": 2")
        assertThat(json).contains("{\"label\": \"Document 1\", \"chunks\": 4}")
        assertThat(json).contains("\"batteryStartPct\": 81")
        assertThat(json).contains("\"query\": \"hello\"")
    }

    @Test
    fun buildStatsJson_escapesSpecialChars() {
        val json = buildStatsJson(
            "MyLeo VS ONNX",
            data.copy(lastQuery = QueryStats("say \"hi\"\nnow", 1, 2)),
            "2026-09-20T00:00:00Z",
        )

        assertThat(json).contains("\"query\": \"say \\\"hi\\\"\\nnow\"")
    }

    @Test
    fun buildStatsJson_emptyState_usesNulls() {
        val json = buildStatsJson(
            "MyLeo VS ONNX",
            StatsReportData(
                device = DeviceSnapshot("unknown", "unknown", onBattery = true, batteryPercent = null),
                modelName = null,
                modelLoadMs = null,
                corpus = CorpusStats(),
                documents = emptyList(),
                lastRun = null,
                lastQuery = null,
            ),
            "2026-09-20T00:00:00Z",
        )

        assertThat(json).contains("\"name\": null")
        assertThat(json).contains("\"lastIngestRun\": null")
        assertThat(json).contains("\"lastSearch\": null")
    }
}
