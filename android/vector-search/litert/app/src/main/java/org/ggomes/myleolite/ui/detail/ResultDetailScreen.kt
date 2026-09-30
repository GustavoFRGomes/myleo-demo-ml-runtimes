package org.ggomes.myleolite.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.ggomes.myleolite.ui.search.SearchHit
import org.ggomes.myleolite.ui.theme.MyLeoLiteTheme

/**
 * Drill-down from a search hit: the full source item (email raw string) with
 * the query terms highlighted plus the match count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultDetailScreen(
    hit: SearchHit,
    query: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Email") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        val (ranges, count) = remember(hit.source, query) { highlightMatches(hit.source, query) }
        val annotated = remember(hit.source, ranges) {
            buildAnnotatedString {
                var cursor = 0
                for (range in ranges.sortedBy { it.first }) {
                    if (range.first > cursor) append(hit.source.substring(cursor, range.first))
                    withStyle(SpanStyle(background = Color.Yellow)) {
                        append(hit.source.substring(range.first, range.last + 1))
                    }
                    cursor = range.last + 1
                }
                if (cursor < hit.source.length) append(hit.source.substring(cursor))
                if (hit.source.isEmpty()) append("")
            }
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "$count ${if (count == 1) "match" else "matches"} for \"$query\"",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(annotated)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ResultDetailPreview() {
    MyLeoLiteTheme {
        ResultDetailScreen(
            hit = SearchHit("hello world", 0.87, "arnold-j hello world email body"),
            query = "hello",
            onBack = {},
        )
    }
}
