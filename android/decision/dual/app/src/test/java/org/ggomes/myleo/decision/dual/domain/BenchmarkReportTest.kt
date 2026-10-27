package org.ggomes.myleo.decision.dual.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.ggomes.myleo.decision.dual.FakeDecider
import org.junit.Test

class BenchmarkReportTest {

    @Test
    fun totals_coverEveryPromptAndTheWallClock() {
        val report =
            report(
                results =
                    listOf(
                        result(QuestionType.CHOICE, index = 0, totalMs = 10.0),
                        result(QuestionType.SCORE, index = 0, totalMs = 20.0),
                        result(QuestionType.NOUL, index = 0, totalMs = 30.0),
                    ),
                startedAt = 1_000L,
                finishedAt = 6_000L,
            )

        assertThat(report.promptMs).isEqualTo(60.0)
        assertThat(report.averageMs).isEqualTo(20.0)
        assertThat(report.minMs).isEqualTo(10.0)
        assertThat(report.maxMs).isEqualTo(30.0)
        assertThat(report.wallMs).isEqualTo(5_000L)
    }

    @Test
    fun aggregates_splitPerformanceByQuestionType() {
        val report =
            report(
                results =
                    listOf(
                        result(QuestionType.CHOICE, index = 0, totalMs = 10.0),
                        result(QuestionType.CHOICE, index = 1, totalMs = 30.0),
                        result(QuestionType.SCORE, index = 0, totalMs = 20.0),
                        result(QuestionType.NOUL, index = 0, totalMs = 40.0),
                    ),
            )

        val choice = report.aggregate(QuestionType.CHOICE)
        assertThat(choice.count).isEqualTo(2)
        assertThat(choice.promptMs).isEqualTo(40.0)
        assertThat(choice.averageMs).isEqualTo(20.0)
        assertThat(choice.minMs).isEqualTo(10.0)
        assertThat(choice.maxMs).isEqualTo(30.0)

        assertThat(report.aggregates.map { it.type })
            .containsExactly(QuestionType.CHOICE, QuestionType.SCORE, QuestionType.NOUL)
        assertThat(report.aggregate(QuestionType.NOUL).count).isEqualTo(1)
    }

    @Test
    fun chargeDelta_isTheDifferenceBetweenTheSamples() {
        val report = report()

        assertThat(report.chargeDeltaUah).isEqualTo(-10_000L)
        assertThat(report.chargeDeltaMah).isEqualTo(-10.0)
    }

    @Test
    fun chargeDelta_isNullWhenTheCounterIsUnavailable() {
        val report =
            report(
                batteryStart = BatterySample(percent = 80, chargeCounterUah = null, currentNowUa = -1, temperatureC = 31f),
                batteryEnd = BatterySample(percent = 79, chargeCounterUah = null, currentNowUa = -1, temperatureC = 31f),
            )

        assertThat(report.chargeDeltaUah).isNull()
        assertThat(report.chargeDeltaMah).isNull()
    }

    @Test
    fun fileNameStem_includesTheRuntime() {
        assertThat(report().fileNameStem).isEqualTo("julia-1-suite-litert")
    }

    @Test
    fun report_rejectsAnEmptyRun() {
        assertThatThrownBy { report(results = emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("at least one prompt")
    }

    private fun result(
        type: QuestionType,
        index: Int,
        totalMs: Double,
        window: Int = 512,
    ): SuiteResult {
        val case = DecisionSuite.of(type)[index]
        val probabilities = DoubleArray(case.question.options.size) { 1.0 / case.question.options.size }
        return SuiteResult(
            case = case,
            outcome =
                FakeDecider.outcome(
                    question = case.question,
                    probabilities = probabilities,
                    choiceId = if (type == QuestionType.CHOICE) case.question.options.first().id else null,
                    score = if (type == QuestionType.SCORE) 1.0 else null,
                    noul = if (type == QuestionType.NOUL) 0.5 else null,
                    window = window,
                    timings = DecisionTimings(1.0, 0.0, 0.0, 0.0, 0.0, totalMs - 1.0, 0.0),
                ),
        )
    }

    private fun report(
        results: List<SuiteResult> =
            listOf(
                result(QuestionType.CHOICE, index = 0, totalMs = 10.0),
                result(QuestionType.SCORE, index = 0, totalMs = 20.0),
                result(QuestionType.NOUL, index = 0, totalMs = 30.0),
            ),
        startedAt: Long = 1_000L,
        finishedAt: Long = 9_000L,
        batteryStart: BatterySample = BatterySample(80, 2_000_000, -350_000, 31.5f),
        batteryEnd: BatterySample = BatterySample(79, 1_990_000, -400_000, 32.0f),
    ): SuiteReport =
        SuiteReport(
            runtimeLabel = "LiteRT",
            backend = "GPU FP32",
            window = 512,
            device = DeviceProfile("TestCorp", "TestPhone", "16", 36),
            loadMs = 1_000.0,
            tokenizerLoadMs = 500.0,
            graphLoadMs = 400.0,
            batteryStart = batteryStart,
            batteryEnd = batteryEnd,
            startedAtEpochMs = startedAt,
            finishedAtEpochMs = finishedAt,
            results = results,
        )
}
