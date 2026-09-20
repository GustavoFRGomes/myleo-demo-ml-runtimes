package org.ggomes.myleolite.ui.ingest

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.ggomes.myleolite.ui.theme.MyLeoLiteTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngestScreen(
    viewModel: IngestViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Prepare corpus") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        IngestContent(
            state = state,
            onCorpusChange = viewModel::onCorpusChange,
            onIngest = viewModel::onIngest,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun IngestContent(
    state: IngestUiState,
    onCorpusChange: (String) -> Unit,
    onIngest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = state.corpusText,
            onValueChange = onCorpusChange,
            label = { Text("Corpus text (blank line = new chunk)") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onIngest,
            enabled = state.corpusText.isNotBlank() && !state.isIngesting,
        ) {
            Text("Calculate embeddings")
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.isIngesting -> CircularProgressIndicator()
            state.error != null -> Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
            )
            state.chunks.isEmpty() -> Text(
                "No chunks yet — paste text and tap Calculate.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.chunks) { chunk ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            chunk.text,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 3,
                        )
                        Text(
                            "${chunk.dimensions} dims · ${chunk.embedMs} ms",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IngestContentPreview() {
    MyLeoLiteTheme {
        IngestContent(
            state = IngestUiState(
                corpusText = "first paragraph\n\nsecond paragraph",
                chunks = listOf(IngestedChunk("first paragraph", 512, 9)),
            ),
            onCorpusChange = {},
            onIngest = {},
        )
    }
}
