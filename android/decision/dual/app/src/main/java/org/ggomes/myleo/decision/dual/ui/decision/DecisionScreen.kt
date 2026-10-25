package org.ggomes.myleo.decision.dual.ui.decision

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.ggomes.myleo.decision.dual.data.DecisionRuntime
import org.ggomes.myleo.decision.dual.domain.DecisionFormat
import org.ggomes.myleo.decision.dual.domain.DecisionOutcome
import org.ggomes.myleo.decision.dual.domain.DecisionPresets
import org.ggomes.myleo.decision.dual.domain.DecisionSuite
import org.ggomes.myleo.decision.dual.domain.DecisionTimings
import org.ggomes.myleo.decision.dual.domain.QuestionType
import org.ggomes.myleo.decision.dual.domain.ReportFormat
import org.ggomes.myleo.decision.dual.domain.SuiteCase
import org.ggomes.myleo.decision.dual.domain.SuiteReport
import org.ggomes.myleo.decision.dual.domain.SuiteResult

/**
 * Julia-1 decision screen: download/load status, presets, an editable state + question list, and
 * one answer card per question with probabilities and per-stage timings. Renders state and sends
 * intents only; all work happens in [DecisionViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionScreen(viewModel: DecisionViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Julia-1 · decisions") }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RuntimePicker(state, viewModel::selectRuntime)
            ModePicker(state, viewModel::selectMode)
            ModelCard(state, viewModel)

            if (state.mode == DecisionMode.CUSTOM) {
                CustomSection(state, viewModel)
            } else {
                SuiteSection(state, viewModel)
            }

            state.error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// MARK: - Custom mode

@Composable
private fun CustomSection(state: DecisionUiState, viewModel: DecisionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PresetRow(viewModel)
        StateField(state.stateText, viewModel::onStateChange)

        state.questions.forEachIndexed { index, draft ->
            QuestionEditor(
                index = index,
                draft = draft,
                canRemove = state.questions.size > 1,
                viewModel = viewModel,
            )
        }
        TextButton(onClick = viewModel::onAddQuestion) { Text("Add question") }

        Button(
            onClick = viewModel::decide,
            enabled = state.canDecide,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isDeciding) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Deciding…")
            } else {
                Text(if (state.questions.size == 1) "Decide" else "Decide all ${state.questions.size}")
            }
        }

        if (state.results.isEmpty() && !state.isDeciding) {
            Text(
                "Julia-1 scores the options you supply in one forward pass — it does not write text. " +
                    "Probabilities are the plain softmax at temperature 1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.results.forEach { outcome -> ResultCard(outcome) }
    }
}

// MARK: - Suite mode

@Composable
private fun ModePicker(state: DecisionUiState, onSelect: (DecisionMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DecisionMode.entries.forEach { mode ->
            FilterChip(
                selected = state.mode == mode,
                onClick = { onSelect(mode) },
                enabled = !state.isBusy,
                label = {
                    Text(
                        if (mode == DecisionMode.CUSTOM) "Custom" else "Suite (${DecisionSuite.Cases.size})",
                    )
                },
            )
        }
    }
}

@Composable
private fun SuiteSection(state: DecisionUiState, viewModel: DecisionViewModel) {
    val suite = state.suite
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Julia-1 prompt suite", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${DecisionSuite.CHOICE_COUNT} multi-answer · ${DecisionSuite.SCORE_COUNT} score · " +
                        "${DecisionSuite.NOUL_COUNT} yes/no. One forward pass per prompt, in order, " +
                        "on ${state.runtime.label}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (suite.running || suite.results.isNotEmpty()) {
                    LinearProgressIndicator(progress = { suite.fraction }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${suite.completed} / ${suite.total} prompts",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (suite.running) {
                            TextButton(onClick = viewModel::cancelSuite) { Text("Cancel") }
                        }
                    }
                    if (!suite.running && suite.completed == suite.total) {
                        Text(
                            "Done · ${DecisionFormat.millis(suite.promptMs)} prompt time · " +
                                "${DecisionFormat.millis(suite.averageMs)} average",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (!suite.running) {
                    Button(
                        onClick = viewModel::runSuite,
                        enabled = state.canRunSuite,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (suite.completed == 0) "Run ${suite.total} prompts" else "Run again")
                    }
                }
            }
        }

        state.reports[state.runtime]?.let { report ->
            ReportCard(report, onExport = viewModel::exportReport)
        }
        if (state.reports.size > 1) {
            ComparisonCard(state.reports)
        }
        state.message?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        QuestionType.entries.forEach { group ->
            val cases = DecisionSuite.of(group)
            Text("${groupLabel(group)} (${cases.size})", style = MaterialTheme.typography.titleSmall)
            cases.forEach { case ->
                SuiteCaseCard(case, suite.results.firstOrNull { it.case.id == case.id })
            }
        }
    }
}

@Composable
private fun SuiteCaseCard(case: SuiteCase, result: SuiteResult?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                case.state,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(case.question.instructions, style = MaterialTheme.typography.bodyMedium)
            if (result == null) {
                Text(
                    "not run",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DecisionFormat.headline(result.outcome),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(DecisionFormat.millis(result.outcome.timings.totalMs), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${result.outcome.window} window · ${result.outcome.tokenCount} tokens · " +
                        result.outcome.accelerator.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun groupLabel(type: QuestionType): String =
    when (type) {
        QuestionType.CHOICE -> "Multi-answer"
        QuestionType.SCORE -> "Score"
        QuestionType.NOUL -> "Yes/no"
    }

// MARK: - Benchmark report

@Composable
private fun ReportCard(report: SuiteReport, onExport: (ReportFormat) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Benchmark · ${report.runtimeLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(report.backend, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "${report.device.manufacturer} ${report.device.model} · Android ${report.device.osVersion} · " +
                    "window ${report.window}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Prompts ${report.results.size} · ${DecisionFormat.millis(report.promptMs)} prompt time · " +
                    "${DecisionFormat.millis(report.averageMs)} avg · " +
                    "${DecisionFormat.millis(report.minMs)}–${DecisionFormat.millis(report.maxMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Wall ${DecisionFormat.millis(report.wallMs.toDouble())} · " +
                    "load ${DecisionFormat.millis(report.loadMs)} " +
                    "(tokenizer ${DecisionFormat.millis(report.tokenizerLoadMs)} · " +
                    "graph ${DecisionFormat.millis(report.graphLoadMs)})",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(batteryLine(report), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            report.aggregates.forEach { aggregate ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        groupLabel(aggregate.type),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${aggregate.count} prompts · avg ${DecisionFormat.millis(aggregate.averageMs)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onExport(ReportFormat.MARKDOWN) }) { Text("Export Markdown") }
                TextButton(onClick = { onExport(ReportFormat.JSON) }) { Text("Export JSON") }
            }
        }
    }
}

@Composable
private fun ComparisonCard(reports: Map<DecisionRuntime, SuiteReport>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Runtime comparison", style = MaterialTheme.typography.titleMedium)
            reports.entries.sortedBy { it.key.ordinal }.forEach { (runtime, report) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        runtime.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${report.backend} · ${DecisionFormat.millis(report.averageMs)} avg · " +
                            chargeText(report),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
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
        report.batteryEnd.currentNowUa?.let {
            String.format(Locale.US, " · %.0f mA at end", it / 1000.0)
        } ?: ""
    return "Battery $percent · $charge$current"
}

private fun chargeText(report: SuiteReport): String =
    report.chargeDeltaMah?.let { String.format(Locale.US, "Δ %.3f mAh", it) } ?: "charge n/a"

// MARK: - Runtime picker

@Composable
private fun RuntimePicker(state: DecisionUiState, onSelect: (DecisionRuntime) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.availableRuntimes.forEach { runtime ->
                FilterChip(
                    selected = runtime == state.runtime,
                    onClick = { onSelect(runtime) },
                    enabled = !state.isBusy && state.status !is ModelStatus.Loading,
                    label = { Text(runtime.label) },
                )
            }
        }
        Text(
            "${state.runtime.backendHint} · each runtime keeps its own download",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// MARK: - Model card

@Composable
private fun ModelCard(state: DecisionUiState, viewModel: DecisionViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Model · ${state.runtime.label}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.isReady) {
                    Text("ready", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            when (val status = state.status) {
                ModelStatus.Idle -> Text("Julia-1 ${state.runtime.label} not loaded yet.", style = MaterialTheme.typography.bodyMedium)
                ModelStatus.Loading ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading the ${state.runtime.label} package…")
                    }
                is ModelStatus.Ready -> {
                    Text("${status.info.backend} · window ${status.info.defaultWindow}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Load ${DecisionFormat.millis(status.info.totalLoadMs)} " +
                            "(tokenizer ${DecisionFormat.millis(status.info.tokenizerLoadMs)} · " +
                            "graph ${DecisionFormat.millis(status.info.graphLoadMs)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ModelStatus.Failed ->
                    Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            when (val download = state.download) {
                is DownloadState.Downloading -> {
                    LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatBytes(download.received)} / ${formatBytes(download.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::cancelDownload) { Text("Cancel") }
                    }
                }
                is DownloadState.Failed ->
                    Text(download.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                DownloadState.Finished, DownloadState.Idle -> Unit
            }
            if (state.canDownload) {
                Button(onClick = viewModel::download) { Text(state.runtime.downloadLabel) }
            }
            if (state.canRetry) {
                TextButton(onClick = viewModel::load) { Text("Retry load") }
            }
        }
    }
}

// MARK: - Presets + state

@Composable
private fun PresetRow(viewModel: DecisionViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DecisionPresets.All.forEach { preset ->
            AssistChip(onClick = { viewModel.applyPreset(preset) }, label = { Text(preset.name) })
        }
    }
}

@Composable
private fun StateField(text: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("State (the text Julia decides about)") },
        minLines = 3,
        maxLines = 8,
    )
}

// MARK: - Question editor

@Composable
private fun QuestionEditor(
    index: Int,
    draft: QuestionDraft,
    canRemove: Boolean,
    viewModel: DecisionViewModel,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (canRemove) {
                    TextButton(onClick = { viewModel.onRemoveQuestion(index) }) { Text("Remove") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuestionType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { viewModel.onTypeChange(index, type) },
                        label = { Text(typeLabel(type)) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.instructions,
                onValueChange = { viewModel.onInstructionsChange(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Question") },
                singleLine = false,
                maxLines = 3,
            )
            draft.options.forEachIndexed { optionIndex, option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = option.text,
                        onValueChange = { viewModel.onOptionChange(index, optionIndex, it) },
                        modifier = Modifier.weight(1f),
                        label = { Text(optionLabel(draft.type, optionIndex)) },
                        singleLine = true,
                    )
                    if (draft.type != QuestionType.NOUL && draft.options.size > 2) {
                        IconButton(onClick = { viewModel.onRemoveOption(index, optionIndex) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove option")
                        }
                    }
                }
            }
            if (draft.type != QuestionType.NOUL && draft.options.size < 20) {
                TextButton(onClick = { viewModel.onAddOption(index) }) { Text("Add option") }
            }
            if (draft.type == QuestionType.NOUL) {
                Text(
                    "Yes/no questions score exactly two options, ordered false then true.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// MARK: - Results

@Composable
private fun ResultCard(outcome: DecisionOutcome) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(outcome.question.instructions, style = MaterialTheme.typography.titleSmall)
            Text(
                DecisionFormat.headline(outcome),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider()
            outcome.question.options.forEachIndexed { index, option ->
                ProbabilityBar(text = option.text, probability = outcome.probabilities[index])
            }
            Text(
                "window ${outcome.window} · ${outcome.tokenCount} tokens · ${outcome.accelerator.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                timingLine(outcome.timings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ProbabilityBar(text: String, probability: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                DecisionFormat.percent(probability),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        LinearProgressIndicator(
            progress = { probability.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}

// MARK: - Formatting

private fun timingLine(timings: DecisionTimings): String = buildString {
    append("prepare ${DecisionFormat.millis(timings.prepareMs)}")
    if (timings.compileMs >= 1.0) append(" · compile ${DecisionFormat.millis(timings.compileMs)}")
    append(" · graph ${DecisionFormat.millis(timings.graphMs)}")
    append(" · decode ${DecisionFormat.millis(timings.decodeMs)}")
    append(" · total ${DecisionFormat.millis(timings.totalMs)}")
}

internal fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000_000L -> String.format(Locale.US, "%.2f GB", bytes / 1e9)
        bytes >= 1_000_000L -> String.format(Locale.US, "%.0f MB", bytes / 1e6)
        else -> String.format(Locale.US, "%.0f kB", bytes / 1e3)
    }

private fun typeLabel(type: QuestionType): String =
    when (type) {
        QuestionType.CHOICE -> "Choice"
        QuestionType.SCORE -> "Score"
        QuestionType.NOUL -> "Yes/no"
    }

private fun optionLabel(type: QuestionType, index: Int): String =
    when (type) {
        QuestionType.CHOICE -> "Option ${index + 1}"
        QuestionType.SCORE -> "Score $index"
        QuestionType.NOUL -> if (index == 0) "false" else "true"
    }
