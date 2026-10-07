package org.ggomes.myleo.vectorsearch.dual

import org.ggomes.myleo.vectorsearch.dual.data.CorpusStats
import org.ggomes.myleo.vectorsearch.dual.data.DocumentStat
import org.ggomes.myleo.vectorsearch.dual.domain.DeviceSnapshot
import org.ggomes.myleo.vectorsearch.dual.domain.QueryStats
import org.ggomes.myleo.vectorsearch.dual.domain.StatsReportData
import org.ggomes.myleo.vectorsearch.dual.domain.buildStatsJson
import org.ggomes.myleo.vectorsearch.dual.domain.buildStatsMarkdown
import org.ggomes.myleo.vectorsearch.dual.domain.escapeJson
import org.ggomes.myleo.vectorsearch.dual.ui.ingest.IngestRunStats
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class StatsReportTest {

    private fun data() = StatsReportData(
        device = DeviceSnapshot("Google", "Pixel 8", onBattery = true, batteryPercent = 62),
        modelName = "embedding.tflite",
        modelLoadMs = 812,
        corpus = CorpusStats(2, 7),
        documents = listOf(DocumentStat("Document 1", 4)),
        lastRun = IngestRunStats("Document 1", 4, 100, 10, 120, 81, 79),
        lastQuery = QueryStats("hello", 5, 42),
    )

    @Test
    fun buildStatsMarkdown_containsSections() {
        val md = buildStatsMarkdown("MyLeo VS Dual", data(), "2026-01-01")

        assertThat(md).contains("MyLeo VS Dual")
        assertThat(md).contains("embedding.tflite")
        assertThat(md).contains("812 ms")
        assertThat(md).contains("Documents: 2, Chunks: 7")
        assertThat(md).contains("81% → 79%")
        assertThat(md).contains("\"hello\": 5 hits in 42 ms")
    }

    @Test
    fun buildStatsJson_isWellFormed() {
        val json = buildStatsJson("MyLeo VS Dual", data(), "2026-01-01")

        assertThat(json).contains("\"app\": \"MyLeo VS Dual\"")
        assertThat(json).contains("\"name\": \"embedding.tflite\"")
        assertThat(json).contains("\"documents\": 2")
        assertThat(json.trim()).startsWith("{").endsWith("}")
    }

    @Test
    fun escapeJson_escapesSpecials() {
        assertThat(escapeJson("a\"b\\c\nd")).isEqualTo("a\\\"b\\\\c\\nd")
    }
}
