package org.ggomes.myleo.vectorsearch.dual.ui.runtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime
import org.ggomes.myleo.vectorsearch.dual.ui.theme.MyLeoVsDualTheme

/**
 * First screen: choose the embedding runtime. Nothing loads until a runtime
 * is tapped — init happens on demand with progress, failures stay on this
 * screen as an error with the other runtime still selectable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimePickerScreen(
    viewModel: RuntimeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("MyLeo Dual") }) },
    ) { innerPadding ->
        RuntimePickerContent(
            state = state,
            onSelect = viewModel::onSelect,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun RuntimePickerContent(
    state: RuntimeUiState,
    onSelect: (InferenceRuntime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Choose an embedding runtime. The model loads only after you choose.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        state.cards.forEach { card ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(card.runtime.label, style = MaterialTheme.typography.titleLarge)
                    Text(card.runtime.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        statusLine(card),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (card.bundled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        val loadingThis = state.isLoading && state.loading == card.runtime
                        Button(
                            onClick = { onSelect(card.runtime) },
                            enabled = !state.isLoading && card.bundled,
                        ) {
                            Text(if (card.loaded) "Use ${card.runtime.label} again" else "Load ${card.runtime.label}")
                        }
                        if (loadingThis) {
                            CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Switching runtimes later clears the shared index.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun statusLine(card: RuntimeCard): String = when {
    card.loadMs != null -> "Loaded in ${card.loadMs} ms"
    card.loaded -> "Loaded"
    card.bundled -> "Model bundled — tap to load"
    else -> "Model not bundled — see README"
}

@Preview(showBackground = true)
@Composable
fun RuntimePickerPreview() {
    MyLeoVsDualTheme {
        RuntimePickerContent(
            state = RuntimeUiState(
                cards = listOf(
                    RuntimeCard(InferenceRuntime.LITERT, bundled = true, loaded = false, loadMs = null),
                    RuntimeCard(InferenceRuntime.ONNX, bundled = false, loaded = false, loadMs = null),
                ),
            ),
            onSelect = {},
        )
    }
}
