package org.ggomes.myleo.llm.litertlm.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.ggomes.myleo.llm.litertlm.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ggomes.myleo.llm.litertlm.domain.ChatMessage
import org.ggomes.myleo.llm.litertlm.domain.ChatRole
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadModel() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Regenerate") },
                            enabled = state.canRegenerate,
                            onClick = { menuOpen = false; viewModel.regenerate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear") },
                            enabled = state.messages.isNotEmpty() && !state.isGenerating,
                            onClick = { menuOpen = false; viewModel.clear() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            StatusBanner(state = state, onDownload = viewModel::downloadModel, onCancelDownload = viewModel::cancelDownload)
            MessageList(messages = state.messages, modifier = Modifier.weight(1f))
            state.lastMetrics?.let { metrics ->
                Text(
                    "TTFT ${metrics.ttftMs} ms · ${"%.1f".format(metrics.tokensPerSecond)} tok/s · ${metrics.generatedTokens} tokens",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            state.error?.let { error ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FloatingActionButton(onClick = { showInfo = true }) {
                    Icon(Icons.Filled.Info, contentDescription = "Info")
                }
            }
            InputBar(
                state = state,
                onInput = viewModel::onInputChange,
                onSend = viewModel::send,
                onCancel = viewModel::cancel,
            )
        }
    }

    if (showSettings) {
        SettingsSheet(state = state, viewModel = viewModel, onDismiss = { showSettings = false })
    }
    if (showInfo) {
        InfoSheet(state = state, onDismiss = { showInfo = false })
    }
}

@Composable
private fun StatusBanner(
    state: ChatUiState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        when (val status = state.status) {
            is ModelStatus.Idle -> Unit
            is ModelStatus.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text("Loading model…", style = MaterialTheme.typography.bodySmall)
            }
            is ModelStatus.Ready -> Text(
                "${status.info.displayName} · ${status.info.backend} · loaded in ${status.info.loadMs} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ModelStatus.Failed -> Column {
                Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                when (val download = state.download) {
                    is DownloadState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { download.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Downloading ${download.received / 1_048_576} / ${download.total / 1_048_576} MB",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onCancelDownload) { Text("Cancel download") }
                    }
                    is DownloadState.Failed -> {
                        Text(download.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onDownload) { Text("Retry download") }
                    }
                    else -> Button(onClick = onDownload) { Text("Download model") }
                }
            }
        }
    }
}

@Composable
private fun MessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { message -> MessageBubble(message) }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.USER
    Row(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.95f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    if (isUser) "You" else "Assistant",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (message.text.isEmpty() && message.isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text(message.text)
                }
                message.metrics?.let { m ->
                    Text(
                        "${m.totalMs} ms · ${"%.1f".format(m.tokensPerSecond)} tok/s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    state: ChatUiState,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            label = { Text("Message") },
            modifier = Modifier.weight(1f),
            enabled = state.status is ModelStatus.Ready,
        )
        if (state.isGenerating) {
            Button(onClick = onCancel) { Text("Stop") }
        } else {
            Button(onClick = onSend, enabled = state.canSend) { Text("Send") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(state: ChatUiState, viewModel: ChatViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.systemPrompt,
                onValueChange = viewModel::onSystemPromptChange,
                label = { Text("System prompt") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            SliderRow("Temperature", state.params.temperature.toFloat(), 0f..2f) {
                viewModel.onParamsChange(state.params.copy(temperature = it.toDouble()))
            }
            SliderRow("Top-p", state.params.topP.toFloat(), 0.05f..1f) {
                viewModel.onParamsChange(state.params.copy(topP = it.toDouble()))
            }
            SliderRow("Top-k", state.params.topK.toFloat(), 1f..100f) {
                viewModel.onParamsChange(state.params.copy(topK = it.roundToInt()))
            }
            SliderRow("Max tokens", state.params.maxTokens.toFloat(), 64f..4096f) {
                viewModel.onParamsChange(state.params.copy(maxTokens = it.roundToInt()))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text("%.2f".format(value), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(state: ChatUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Info", style = MaterialTheme.typography.titleMedium)
            when (val status = state.status) {
                is ModelStatus.Ready -> {
                    Text("Model: ${status.info.displayName}", style = MaterialTheme.typography.bodyMedium)
                    Text("Backend: ${status.info.backend}", style = MaterialTheme.typography.bodyMedium)
                    Text("Size: ${formatBytes(status.info.sizeBytes)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Loaded in: ${status.info.loadMs} ms", style = MaterialTheme.typography.bodyMedium)
                    if (status.info.maxContextTokens > 0) {
                        Text(
                            "Context: ${status.info.maxContextTokens} tokens",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    status.info.sourceUrl?.let { Text("Source: $it", style = MaterialTheme.typography.bodySmall) }
                }
                else -> Text("Model not loaded", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Generations", style = MaterialTheme.typography.titleSmall)
            val assistants = state.messages.filter { it.role == ChatRole.ASSISTANT }
            if (assistants.isEmpty()) {
                Text("No generations yet", style = MaterialTheme.typography.bodySmall)
            }
            assistants.forEach { message ->
                Text("• ${message.text.take(80)}", style = MaterialTheme.typography.bodySmall)
                message.metrics?.let { m ->
                    Text(
                        "   TTFT ${m.ttftMs} ms · total ${m.totalMs} ms · ${"%.1f".format(m.tokensPerSecond)} tok/s · ${m.generatedTokens} tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
    else -> "$bytes B"
}
