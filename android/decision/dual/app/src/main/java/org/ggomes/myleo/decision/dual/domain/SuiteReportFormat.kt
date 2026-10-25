package org.ggomes.myleo.decision.dual.domain

import java.util.Locale

/** Export format for a [SuiteReport]. */
enum class ReportFormat(val extension: String, val mimeType: String) {
    MARKDOWN("md", "text/markdown"),
    JSON("json", "application/json"),
}

/**
 * Pure serializers for a benchmark report. Both include the runtime/backend, device, load
 * timings, per-type aggregates, battery delta and every prompt's answer + timings, so a report
 * can be compared across runtimes without the app.
 */
object SuiteReportFormat {

    fun markdown(report: SuiteReport): String = buildString {
        appendLine("# Julia-1 suite — ${report.runtimeLabel}")
        appendLine()
        appendLine("- Backend: ${report.backend}")
        appendLine("- Window: ${report.window} tokens")
        appendLine(
            "- Device: ${report.device.manufacturer} ${report.device.model} · " +
                "Android ${report.device.osVersion} (SDK ${report.device.sdk})",
        )
        appendLine(
            "- Load: ${ms(report.loadMs)} total (tokenizer ${ms(report.tokenizerLoadMs)}, " +
                "graph ${ms(report.graphLoadMs)})",
        )
        appendLine(
            "- Prompts: ${report.results.size} · ${ms(report.promptMs)} prompt time · " +
                "${ms(report.averageMs)} average · ${ms(report.minMs)}–${ms(report.maxMs)}",
        )
        appendLine("- Wall clock: ${ms(report.wallMs.toDouble())}")
        appendLine("- Battery: ${batteryLine(report)}")
        appendLine()
        appendLine("## Performance by question type")
        appendLine()
        appendLine("| Type | Count | Total | Average | Min | Max |")
        appendLine("|---|---:|---:|---:|---:|---:|")
        report.aggregates.forEach { aggregate ->
            appendLine(
                "| ${typeLabel(aggregate.type)} | ${aggregate.count} | ${ms(aggregate.promptMs)} | " +
                    "${ms(aggregate.averageMs)} | ${ms(aggregate.minMs)} | ${ms(aggregate.maxMs)} |",
            )
        }
        appendLine()
        appendLine("## Prompts")
        appendLine()
        appendLine("| ID | Type | Tokens | Window | Total | Answer |")
        appendLine("|---|---|---:|---:|---:|---|")
        report.results.forEach { row ->
            appendLine(
                "| ${row.case.id} | ${typeLabel(row.case.group)} | ${row.outcome.tokenCount} | " +
                    "${row.outcome.window} | ${ms(row.outcome.timings.totalMs)} | " +
                    "${DecisionFormat.headline(row.outcome).replace("|", "\\|")} |",
            )
        }
    }

    fun json(report: SuiteReport): String = buildString {
        appendLine("{")
        appendLine("  \"schema\": \"myleo.julia1.suite/1\",")
        appendLine("  \"runtime\": ${quote(report.runtimeLabel)},")
        appendLine("  \"backend\": ${quote(report.backend)},")
        appendLine("  \"window\": ${report.window},")
        appendLine("  \"device\": {")
        appendLine("    \"manufacturer\": ${quote(report.device.manufacturer)},")
        appendLine("    \"model\": ${quote(report.device.model)},")
        appendLine("    \"osVersion\": ${quote(report.device.osVersion)},")
        appendLine("    \"sdk\": ${report.device.sdk}")
        appendLine("  },")
        appendLine("  \"loadMs\": ${number(report.loadMs)},")
        appendLine("  \"tokenizerLoadMs\": ${number(report.tokenizerLoadMs)},")
        appendLine("  \"graphLoadMs\": ${number(report.graphLoadMs)},")
        appendLine("  \"performance\": {")
        appendLine("    \"promptMs\": ${number(report.promptMs)},")
        appendLine("    \"wallMs\": ${report.wallMs},")
        appendLine("    \"averageMs\": ${number(report.averageMs)},")
        appendLine("    \"minMs\": ${number(report.minMs)},")
        appendLine("    \"maxMs\": ${number(report.maxMs)},")
        appendLine("    \"byType\": [")
        report.aggregates.forEachIndexed { index, aggregate ->
            val comma = if (index == report.aggregates.lastIndex) "" else ","
            appendLine(
                "      {\"type\": ${quote(aggregate.type.wire)}, \"count\": ${aggregate.count}, " +
                    "\"promptMs\": ${number(aggregate.promptMs)}, " +
                    "\"averageMs\": ${number(aggregate.averageMs)}, " +
                    "\"minMs\": ${number(aggregate.minMs)}, " +
                    "\"maxMs\": ${number(aggregate.maxMs)}}$comma",
            )
        }
        appendLine("    ]")
        appendLine("  },")
        appendLine("  \"battery\": {")
        appendLine("    \"start\": ${batteryJson(report.batteryStart)},")
        appendLine("    \"end\": ${batteryJson(report.batteryEnd)},")
        appendLine("    \"chargeDeltaUah\": ${report.chargeDeltaUah ?: "null"}")
        appendLine("  },")
        appendLine("  \"startedAtEpochMs\": ${report.startedAtEpochMs},")
        appendLine("  \"finishedAtEpochMs\": ${report.finishedAtEpochMs},")
        appendLine("  \"prompts\": [")
        report.results.forEachIndexed { index, row ->
            val comma = if (index == report.results.lastIndex) "" else ","
            appendLine(
                "    {\"id\": ${quote(row.case.id)}, \"type\": ${quote(row.case.group.wire)}, " +
                    "\"tokens\": ${row.outcome.tokenCount}, \"window\": ${row.outcome.window}, " +
                    "\"totalMs\": ${number(row.outcome.timings.totalMs)}, " +
                    "\"answer\": ${quote(DecisionFormat.headline(row.outcome))}}$comma",
            )
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun batteryLine(report: SuiteReport): String {
        val percent = "${report.batteryStart.percent ?: "?"}% → ${report.batteryEnd.percent ?: "?"}%"
        val delta = report.chargeDeltaMah
        val charge =
            if (delta == null) {
                "charge counter unavailable"
            } else {
                String.format(Locale.US, "charge Δ %.3f mAh", delta)
            }
        val current =
            report.batteryEnd.currentNowUa?.let { String.format(Locale.US, " · %.0f mA at end", it / 1000.0) }
                ?: ""
        return "$percent · $charge$current"
    }

    private fun batteryJson(sample: BatterySample): String =
        "{\"percent\": ${sample.percent ?: "null"}, " +
            "\"chargeCounterUah\": ${sample.chargeCounterUah ?: "null"}, " +
            "\"currentNowUa\": ${sample.currentNowUa ?: "null"}, " +
            "\"temperatureC\": ${sample.temperatureC?.let { number(it.toDouble()) } ?: "null"}}"

    private fun typeLabel(type: QuestionType): String =
        when (type) {
            QuestionType.CHOICE -> "multi-answer"
            QuestionType.SCORE -> "score"
            QuestionType.NOUL -> "yes/no"
        }

    private fun ms(value: Double): String = String.format(Locale.US, "%.0f ms", value)

    private fun number(value: Double): String = String.format(Locale.US, "%.3f", value)

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}
