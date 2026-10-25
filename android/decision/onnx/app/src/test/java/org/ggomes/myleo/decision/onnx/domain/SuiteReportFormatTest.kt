package org.ggomes.myleo.decision.onnx.domain

import org.assertj.core.api.Assertions.assertThat
import org.ggomes.myleo.decision.onnx.FakeDecider
import org.ggomes.myleo.decision.onnx.data.JuliaJson
import org.junit.Test

class SuiteReportFormatTest {

    @Test
    fun markdown_containsTheHeaderDeviceAndPerTypeRows() {
        val markdown = SuiteReportFormat.markdown(report())

        assertThat(markdown).contains("# Julia-1 suite — ONNX")
        assertThat(markdown).contains("Backend: CPU")
        assertThat(markdown).contains("TestCorp TestPhone")
        assertThat(markdown).contains("| multi-answer | 8 |")
        assertThat(markdown).contains("| score | 5 |")
        assertThat(markdown).contains("| yes/no | 5 |")
        assertThat(markdown).contains("## Prompts")
        assertThat(markdown).contains("route-billing")
        assertThat(markdown).contains("Battery: 80% → 79%")
        assertThat(markdown).contains("charge Δ -10.000 mAh")
    }

    @Test
    fun json_isParseableAndCarriesEveryPromptAndAggregate() {
        val json = SuiteReportFormat.json(report())

        val root = JuliaJson.asObject(JuliaJson.parse(json))
        assertThat(root["schema"]).isEqualTo("myleo.julia1.suite/1")
        assertThat(root["runtime"]).isEqualTo("ONNX")
        assertThat(root["backend"]).isEqualTo("CPU")
        assertThat(root["window"]).isEqualTo(1024L)
        assertThat(JuliaJson.asArray(root["prompts"])).hasSize(18)
        assertThat(JuliaJson.asArray(JuliaJson.asObject(root["performance"])["byType"])).hasSize(3)
        val battery = JuliaJson.asObject(root["battery"])
        assertThat((battery["chargeDeltaUah"] as Number).toLong()).isEqualTo(-10_000L)
    }

    @Test
    fun json_escapesQuotesBackslashesAndNewlines() {
        val device = DeviceProfile("Corp \"Q\"", "Pixel\\9\nPro", "16", 36)

        val root = JuliaJson.asObject(JuliaJson.parse(SuiteReportFormat.json(report(device = device))))
        val parsed = JuliaJson.asObject(root["device"])

        assertThat(parsed["manufacturer"]).isEqualTo("Corp \"Q\"")
        assertThat(parsed["model"]).isEqualTo("Pixel\\9\nPro")
    }

    @Test
    fun markdown_escapesPipesInAnswerText() {
        val question =
            DecisionQuestion.choice(
                "Which option?",
                listOf("a" to "A | B", "b" to "B"),
            )
        val case = SuiteCase(id = "pipe", group = QuestionType.CHOICE, state = "state", question = question)
        val outcome =
            FakeDecider.outcome(
                question = question,
                probabilities = doubleArrayOf(0.9, 0.1),
                choiceId = "a",
                argmax = 0,
            )

        val markdown = SuiteReportFormat.markdown(report(results = listOf(SuiteResult(case, outcome))))

        assertThat(markdown).contains("A \\| B")
    }

    private fun report(
        device: DeviceProfile = DeviceProfile("TestCorp", "TestPhone", "16", 36),
        results: List<SuiteResult> = DecisionSuite.Cases.map { case ->
            val probabilities = DoubleArray(case.question.options.size) { 1.0 / case.question.options.size }
            SuiteResult(
                case = case,
                outcome =
                    FakeDecider.outcome(
                        question = case.question,
                        probabilities = probabilities,
                        choiceId = if (case.group == QuestionType.CHOICE) case.question.options.first().id else null,
                        score = if (case.group == QuestionType.SCORE) 1.0 else null,
                        noul = if (case.group == QuestionType.NOUL) 0.5 else null,
                        timings = DecisionTimings(1.0, 0.0, 0.0, 0.0, 0.0, 19.0, 0.0),
                    ),
            )
        },
    ): SuiteReport =
        SuiteReport(
            runtimeLabel = "ONNX",
            backend = "CPU",
            window = 1024,
            device = device,
            loadMs = 1_000.0,
            tokenizerLoadMs = 500.0,
            graphLoadMs = 400.0,
            batteryStart = BatterySample(80, 2_000_000, -350_000, 31.5f),
            batteryEnd = BatterySample(79, 1_990_000, -400_000, 32.0f),
            startedAtEpochMs = 1_000L,
            finishedAtEpochMs = 9_000L,
            results = results,
        )
}
