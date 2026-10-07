package org.ggomes.myleo.vectorsearch.onnx

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ggomes.myleo.vectorsearch.onnx.data.AndroidSystemStatsProvider
import org.ggomes.myleo.vectorsearch.onnx.data.DefaultEmbeddingRepository
import org.ggomes.myleo.vectorsearch.onnx.data.DocumentRepository
import org.ggomes.myleo.vectorsearch.onnx.data.MissingModelEmbedder
import org.ggomes.myleo.vectorsearch.onnx.data.ObjectBoxDocumentRepository
import org.ggomes.myleo.vectorsearch.onnx.data.OnnxEmbedderFactory
import org.ggomes.myleo.vectorsearch.onnx.data.StatsExporter
import org.ggomes.myleo.vectorsearch.onnx.data.db.ObjectBoxStores
import org.ggomes.myleo.vectorsearch.onnx.domain.AssetEnronDatasetLoader
import org.ggomes.myleo.vectorsearch.onnx.domain.CalculateEmbeddingUseCase
import org.ggomes.myleo.vectorsearch.onnx.domain.EmailDatasetLoader
import org.ggomes.myleo.vectorsearch.onnx.domain.Embedder
import org.ggomes.myleo.vectorsearch.onnx.domain.QueryStats
import org.ggomes.myleo.vectorsearch.onnx.domain.StatsReportData
import org.ggomes.myleo.vectorsearch.onnx.domain.SystemStatsProvider
import org.ggomes.myleo.vectorsearch.onnx.domain.buildStatsJson
import org.ggomes.myleo.vectorsearch.onnx.domain.buildStatsMarkdown
import org.ggomes.myleo.vectorsearch.onnx.ui.AppScreen
import org.ggomes.myleo.vectorsearch.onnx.ui.detail.ResultDetailScreen
import org.ggomes.myleo.vectorsearch.onnx.ui.ingest.IngestScreen
import org.ggomes.myleo.vectorsearch.onnx.ui.ingest.IngestViewModel
import org.ggomes.myleo.vectorsearch.onnx.ui.search.SearchHit
import org.ggomes.myleo.vectorsearch.onnx.ui.search.SearchScreen
import org.ggomes.myleo.vectorsearch.onnx.ui.search.SearchViewModel
import org.ggomes.myleo.vectorsearch.onnx.ui.stats.StatsFormat
import org.ggomes.myleo.vectorsearch.onnx.ui.stats.StatsSheetContent
import org.ggomes.myleo.vectorsearch.onnx.ui.theme.MyLeoVsOnnxTheme
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis

class MainActivity : ComponentActivity() {

    private val systemStats: SystemStatsProvider by lazy {
        AndroidSystemStatsProvider(applicationContext)
    }

    private val useCase: CalculateEmbeddingUseCase by lazy {
        var loadMs: Long? = null
        var name: String? = null
        val embedder = try {
            lateinit var loaded: Embedder
            loadMs = measureTimeMillis {
                loaded = OnnxEmbedderFactory.create(applicationContext)
            }
            name = OnnxEmbedderFactory.DEFAULT_MODEL_ASSET_PATH.substringAfterLast("/")
            Log.d(TAG, "embedding model loaded in ${loadMs}ms")
            loaded
        } catch (e: Exception) {
            Log.w(TAG, "model assets missing, embeddings will fail: ${e.message}")
            MissingModelEmbedder(e.message ?: "model assets missing")
        }
        modelLoadMs = loadMs
        modelName = name
        CalculateEmbeddingUseCase(DefaultEmbeddingRepository(embedder))
    }

    private var modelLoadMs: Long? = null
    private var modelName: String? = null

    private val documentRepository: DocumentRepository by lazy {
        ObjectBoxDocumentRepository(ObjectBoxStores.get(applicationContext))
    }

    private val datasetLoader: EmailDatasetLoader by lazy {
        AssetEnronDatasetLoader(applicationContext)
    }

    private val vmFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
                modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                    SearchViewModel(useCase, documentRepository)
                modelClass.isAssignableFrom(IngestViewModel::class.java) ->
                    IngestViewModel(
                        useCase,
                        documentRepository,
                        modelLoadMs = modelLoadMs,
                        modelName = modelName,
                        systemStats = systemStats,
                        datasetLoader = datasetLoader,
                    )
                else -> throw IllegalArgumentException("unknown ${modelClass.simpleName}")
            } as T
        }
    }

    private val searchViewModel: SearchViewModel by lazy {
        ViewModelProvider(this, vmFactory)[SearchViewModel::class.java]
    }

    private val ingestViewModel: IngestViewModel by lazy {
        ViewModelProvider(this, vmFactory)[IngestViewModel::class.java]
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var screen by rememberSaveable { mutableStateOf(AppScreen.Search) }
            var showStats by rememberSaveable { mutableStateOf(false) }
            var selectedHit by androidx.compose.runtime.remember { mutableStateOf<SearchHit?>(null) }
            MyLeoVsOnnxTheme {
                when (screen) {
                    AppScreen.Search -> SearchScreen(
                        viewModel = searchViewModel,
                        onOpenIngest = { screen = AppScreen.Ingest },
                        onShowStats = { showStats = true },
                        onOpenHit = { selectedHit = it },
                    )
                    AppScreen.Ingest -> IngestScreen(
                        viewModel = ingestViewModel,
                        onBack = { screen = AppScreen.Search },
                    )
                }
                selectedHit?.let { hit ->
                    ResultDetailScreen(
                        hit = hit,
                        query = searchViewModel.uiState.collectAsState().value.query,
                        onBack = { selectedHit = null },
                    )
                }
                if (showStats) {
                    ModalBottomSheet(onDismissRequest = { showStats = false }) {
                        val ingest by ingestViewModel.uiState.collectAsState()
                        val search by searchViewModel.uiState.collectAsState()
                        StatsSheetContent(
                            data = StatsReportData(
                                device = systemStats.snapshot(),
                                modelName = ingest.modelName,
                                modelLoadMs = ingest.modelLoadMs,
                                corpus = ingest.corpus,
                                documents = ingest.documents,
                                lastRun = ingest.lastRun,
                                lastQuery = search.queryMs?.let {
                                    QueryStats(search.query, search.hits.size, it)
                                },
                            ),
                            onExport = { format -> exportStats(format) },
                        )
                    }
                }
            }
        }
    }

    private fun exportStats(format: StatsFormat) {
        val generatedAt = Instant.now().toString()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val (fileName, mimeType, content) = when (format) {
            StatsFormat.MARKDOWN -> Triple(
                "myleo-stats-$stamp.md",
                "text/markdown",
                buildStatsMarkdown(APP_LABEL, reportData(), generatedAt),
            )
            StatsFormat.JSON -> Triple(
                "myleo-stats-$stamp.json",
                "application/json",
                buildStatsJson(APP_LABEL, reportData(), generatedAt),
            )
        }
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                StatsExporter(this@MainActivity).export(fileName, mimeType, content).getOrNull()
            }
            if (uri == null) {
                Toast.makeText(this@MainActivity, "Export failed", Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(this@MainActivity, "Saved $fileName to Downloads", Toast.LENGTH_LONG).show()
            val share = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "Share statistics"))
        }
    }

    private fun reportData(): StatsReportData {
        val ingest = ingestViewModel.uiState.value
        val search = searchViewModel.uiState.value
        return StatsReportData(
            device = systemStats.snapshot(),
            modelName = ingest.modelName,
            modelLoadMs = ingest.modelLoadMs,
            corpus = ingest.corpus,
            documents = ingest.documents,
            lastRun = ingest.lastRun,
            lastQuery = search.queryMs?.let { QueryStats(search.query, search.hits.size, it) },
        )
    }

    companion object {
        private const val TAG = "MyLeo-Model"
        private const val APP_LABEL = "MyLeo VS ONNX"
    }
}
