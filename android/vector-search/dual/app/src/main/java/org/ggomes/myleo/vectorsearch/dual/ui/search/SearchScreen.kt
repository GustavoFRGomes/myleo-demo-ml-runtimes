package org.ggomes.myleo.vectorsearch.dual.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime
import org.ggomes.myleo.vectorsearch.dual.ui.theme.MyLeoVsDualTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    runtime: InferenceRuntime,
    onSwitchRuntime: (InferenceRuntime) -> Unit,
    onOpenIngest: () -> Unit,
    onShowStats: () -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("MyLeo Dual") },
                actions = {
                    // Active runtime chip; tap to switch (switching clears the index).
                    FilterChip(
                        selected = false,
                        onClick = { menuOpen = true },
                        label = { Text(runtime.label) },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        InferenceRuntime.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label + if (option == runtime) " ✓" else "") },
                                onClick = {
                                    menuOpen = false
                                    if (option != runtime) onSwitchRuntime(option)
                                },
                            )
                        }
                    }
                    IconButton(onClick = onOpenIngest) {
                        Icon(Icons.Filled.Settings, contentDescription = "Prepare corpus")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onShowStats) {
                Icon(Icons.Filled.Info, contentDescription = "Statistics")
            }
        },
    ) { innerPadding ->
        SearchContent(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            onOpenHit = onOpenHit,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Search query") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onSearch,
                enabled = state.query.isNotBlank() && !state.isSearching,
            ) {
                Text("Search")
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.isSearching -> CircularProgressIndicator()
            state.error != null -> Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
            )
            state.hits.isEmpty() -> Text(
                "Index is empty — tap the gear to ingest text first.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.queryMs?.let { ms ->
            Text(
                "Ranked ${state.hits.size} hits in $ms ms",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.hits) { hit ->
                Card(onClick = { onOpenHit(hit) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(hit.text, style = MaterialTheme.typography.titleSmall, maxLines = 3)
                        Text(
                            "${(hit.similarity * 100).roundToInt()}% match",
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
fun SearchContentPreview() {
    MyLeoVsDualTheme {
        SearchContent(
            state = SearchUiState(
                query = "hello",
                hits = listOf(SearchHit("hello world", 0.87)),
                queryMs = 14,
            ),
            onQueryChange = {},
            onSearch = {},
            onOpenHit = {},
        )
    }
}
