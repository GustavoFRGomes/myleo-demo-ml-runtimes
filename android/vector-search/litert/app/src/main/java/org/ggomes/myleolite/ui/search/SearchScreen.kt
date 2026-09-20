package org.ggomes.myleolite.ui.search

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.ggomes.myleolite.ui.theme.MyLeoLiteTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenIngest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                // Explicit runtime name: app_name ("MyLeo Lite") doesn't say LiteRT.
                title = { Text("MyLeo LiteRT") },
                actions = {
                    IconButton(onClick = onOpenIngest) {
                        Icon(Icons.Filled.Settings, contentDescription = "Prepare corpus")
                    }
                },
            )
        },
    ) { innerPadding ->
        SearchContent(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
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
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.hits) { hit ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(hit.text, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${hit.dimensions} dims · ${hit.embedMs} ms",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            hit.preview.joinToString(prefix = "[", postfix = ", …]"),
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
    MyLeoLiteTheme {
        SearchContent(
            state = SearchUiState(
                query = "hello",
                hits = listOf(EmbeddingHit("hello", 512, 12, listOf(0.1f, -0.2f))),
            ),
            onQueryChange = {},
            onSearch = {},
        )
    }
}
