package org.ggomes.myleo.vectorsearch.dual

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ggomes.myleo.vectorsearch.dual.data.AndroidSystemStatsProvider
import org.ggomes.myleo.vectorsearch.dual.data.DefaultEmbeddingRepository
import org.ggomes.myleo.vectorsearch.dual.data.DocumentRepository
import org.ggomes.myleo.vectorsearch.dual.data.LiteRtEmbedderFactory
import org.ggomes.myleo.vectorsearch.dual.data.LoadedRuntime
import org.ggomes.myleo.vectorsearch.dual.data.ObjectBoxDocumentRepository
import org.ggomes.myleo.vectorsearch.dual.data.OnnxEmbedderFactory
import org.ggomes.myleo.vectorsearch.dual.data.OnnxExecutionProvider
import org.ggomes.myleo.vectorsearch.dual.data.RuntimeManager
import org.ggomes.myleo.vectorsearch.dual.data.StatsExporter
import org.ggomes.myleo.vectorsearch.dual.data.db.ObjectBoxStores
import org.ggomes.myleo.vectorsearch.dual.domain.AssetEnronDatasetLoader
import org.ggomes.myleo.vectorsearch.dual.domain.CalculateEmbeddingUseCase
import org.ggomes.myleo.vectorsearch.dual.domain.EmailDatasetLoader
import org.ggomes.myleo.vectorsearch.dual.domain.Embedder
import org.ggomes.myleo.vectorsearch.dual.domain.InferenceRuntime
import org.ggomes.myleo.vectorsearch.dual.domain.QueryStats
import org.ggomes.myleo.vectorsearch.dual.domain.StatsReportData
import org.ggomes.myleo.vectorsearch.dual.domain.SystemStatsProvider
import org.ggomes.myleo.vectorsearch.dual.domain.buildStatsJson
import org.ggomes.myleo.vectorsearch.dual.domain.buildStatsMarkdown
import org.ggomes.myleo.vectorsearch.dual.ui.AppScreen
import org.ggomes.myleo.vectorsearch.dual.ui.detail.ResultDetailScreen
import org.ggomes.myleo.vectorsearch.dual.ui.ingest.IngestScreen
import org.ggomes.myleo.vectorsearch.dual.ui.ingest.IngestViewModel
import org.ggomes.myleo.vectorsearch.dual.ui.runtime.RuntimePickerScreen
import org.ggomes.myleo.vectorsearch.dual.ui.runtime.RuntimeViewModel
import org.ggomes.myleo.vectorsearch.dual.ui.search.SearchHit
import org.ggomes.myleo.vectorsearch.dual.ui.search.SearchScreen
import org.ggomes.myleo.vectorsearch.dual.ui.search.SearchViewModel
import org.ggomes.myleo.vectorsearch.dual.ui.stats.StatsFormat
import org.ggomes.myleo.vectorsearch.dual.ui.stats.StatsSheetContent
import org.ggomes.myleo.vectorsearch.dual.ui.theme.MyLeoVsDualTheme
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis

/** Per-runtime screen + inference stack. Cached so switching back keeps state. */
private data class RuntimeDeps(
    val runtime: InferenceRuntime,
    val useCase: CalculateEmbeddingUseCase,
    val searchViewModel: SearchViewModel,
    val ingestViewModel: IngestViewModel,
    val modelName: String,
    val modelLoadMs: Long,
)

class MainActivity : ComponentActivity() {

    private val systemStats: SystemStatsProvider by lazy {
        AndroidSystemStatsProvider(applicationContext)
    }

    private val datasetLoader: EmailDatasetLoader by lazy {
        AssetEnronDatasetLoader(applicationContext)
    }

    private val runtimeManager: RuntimeManager by lazy {
        RuntimeManager(
            documents = ObjectBoxDocumentRepository(ObjectBoxStores.get(applicationContext)),
            load = { runtime -> loadRuntime(runtime) },
            hasModelAssets = { runtime -> hasModelAssets(runtime) },
        )
    }

    private val deps = mutableMapOf<InferenceRuntime, RuntimeDeps>()

    private var activeRuntime: InferenceRuntime? = null
    private var onRuntimeReady: ((InferenceRuntime) -> Unit)? = null

    private fun viewModelsFor(runtime: InferenceRuntime, loaded: LoadedRuntime): RuntimeDeps =
        deps.getOrPut(runtime) {
            val useCase = CalculateEmbeddingUseCase(DefaultEmbeddingRepository(loaded.embedder))
            val factory = vmFactory(useCase, loaded.modelName, loaded.modelLoadMs)
            RuntimeDeps(
                runtime = runtime,
                useCase = useCase,
                searchViewModel = ViewModelProvider(
                    this,
                    factory,
                )["search-${runtime.name}", SearchViewModel::class.java],
                ingestViewModel = ViewModelProvider(
                    this,
                    factory,
                )["ingest-${runtime.name}", IngestViewModel::class.java],
                modelName = loaded.modelName,
                modelLoadMs = loaded.modelLoadMs,
            )
        }

    /** Ingest VMs need model + stats + dataset wiring beyond the shared use case. */
    private fun vmFactory(
        useCase: CalculateEmbeddingUseCase,
        modelName: String? = null,
        modelLoadMs: Long? = null,
    ) =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
                modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                    SearchViewModel(useCase, runtimeManager.documents)
                modelClass.isAssignableFrom(IngestViewModel::class.java) ->
                    IngestViewModel(
                        useCase,
                        runtimeManager.documents,
                        modelLoadMs = modelLoadMs,
                        modelName = modelName,
                        systemStats = systemStats,
                        datasetLoader = datasetLoader,
                    )
                modelClass.isAssignableFrom(RuntimeViewModel::class.java) ->
                    RuntimeViewModel(
                        runtimeManager,
                        onLoaded = { runtime, loaded -> onRuntimeLoaded(runtime, loaded) },
                    )
                else -> throw IllegalArgumentException("unknown ${modelClass.simpleName}")
            } as T
        }

    private fun onRuntimeLoaded(runtime: InferenceRuntime, loaded: LoadedRuntime) {
        activeRuntime = runtime
        viewModelsFor(runtime, loaded)
        onRuntimeReady?.invoke(runtime)
    }

    private fun loadRuntime(runtime: InferenceRuntime): LoadedRuntime {
        lateinit var embedder: Embedder
        val name: String
        val ms = measureTimeMillis {
            when (runtime) {
                InferenceRuntime.LITERT -> {
                    embedder = LiteRtEmbedderFactory.create(applicationContext)
                    name = LiteRtEmbedderFactory.DEFAULT_MODEL_ASSET_PATH.substringAfterLast("/")
                }
                InferenceRuntime.ONNX -> {
                    val provider = OnnxExecutionProvider.fromExtra(intent.getStringExtra("onnx_ep"))
                    val created = OnnxEmbedderFactory.create(applicationContext, provider = provider)
                    embedder = created
                    name = OnnxEmbedderFactory.DEFAULT_MODEL_ASSET_PATH.substringAfterLast("/") +
                        " · " + created.providerLabel
                }
            }
        }
        Log.d(TAG, "${runtime.label} embedding model loaded in ${ms}ms")
        return LoadedRuntime(embedder, name, ms)
    }

    private fun hasModelAssets(runtime: InferenceRuntime): Boolean {
        val (model, vocab) = when (runtime) {
            InferenceRuntime.LITERT -> LiteRtEmbedderFactory.DEFAULT_MODEL_ASSET_PATH to
                LiteRtEmbedderFactory.DEFAULT_VOCAB_ASSET_PATH
            InferenceRuntime.ONNX -> OnnxEmbedderFactory.DEFAULT_MODEL_ASSET_PATH to
                OnnxEmbedderFactory.DEFAULT_VOCAB_ASSET_PATH
        }
        // Stream probe: openFd requires uncompressed (Stored) entries, but
        // aapt may deflate large assets (e.g. .onnx) — streaming works either way.
        return try {
            applicationContext.assets.open(model).close()
            applicationContext.assets.open(vocab).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private val runtimePickerViewModel: RuntimeViewModel by lazy {
        ViewModelProvider(this, vmFactory(pickerUseCase()))[RuntimeViewModel::class.java]
    }

    /** Placeholder use case: the picker never embeds, it only selects runtimes. */
    private fun pickerUseCase(): CalculateEmbeddingUseCase =
        CalculateEmbeddingUseCase(
            DefaultEmbeddingRepository(
                object : Embedder {
                    override fun embed(text: String): List<Float> =
                        throw IllegalStateException("no runtime selected")
                },
            ),
        )

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var screen by rememberSaveable { mutableStateOf(AppScreen.RuntimePicker) }
            var runtime by remember { mutableStateOf<InferenceRuntime?>(null) }
            var showStats by rememberSaveable { mutableStateOf(false) }
            var selectedHit by remember { mutableStateOf<SearchHit?>(null) }
            var pendingSwitch by remember { mutableStateOf<InferenceRuntime?>(null) }
            MyLeoVsDualTheme {
                onRuntimeReady = { ready ->
                    runtime = ready
                    screen = AppScreen.Search
                    runtimePickerViewModel.refresh()
                }
                when (screen) {
                    AppScreen.RuntimePicker -> RuntimePickerScreen(viewModel = runtimePickerViewModel)
                    AppScreen.Search -> {
                        val current = runtime ?: return@MyLeoVsDualTheme
                        val currentDeps = deps[current] ?: return@MyLeoVsDualTheme
                        SearchScreen(
                            viewModel = currentDeps.searchViewModel,
                            runtime = current,
                            onSwitchRuntime = { pendingSwitch = it },
                            onOpenIngest = { screen = AppScreen.Ingest },
                            onShowStats = { showStats = true },
                            onOpenHit = { selectedHit = it },
                        )
                    }
                    AppScreen.Ingest -> {
                        val current = runtime ?: return@MyLeoVsDualTheme
                        val currentDeps = deps[current] ?: return@MyLeoVsDualTheme
                        IngestScreen(
                            viewModel = currentDeps.ingestViewModel,
                            runtime = current,
                            onBack = { screen = AppScreen.Search },
                        )
                    }
                }
                // Switch confirmation: the index is shared, so switching clears it.
                pendingSwitch?.let { target ->
                    AlertDialog(
                        onDismissRequest = { pendingSwitch = null },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    pendingSwitch = null
                                    switchRuntime(target) { ok ->
                                        if (ok) {
                                            runtime = target
                                            screen = AppScreen.Search
                                            runtimePickerViewModel.refresh()
                                        }
                                    }
                                },
                            ) { Text("Switch & clear index") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingSwitch = null }) { Text("Cancel") }
                        },
                        title = { Text("Switch to ${target.label}?") },
                        text = { Text("The index is shared between runtimes — switching clears it. The ${target.label} model stays loaded for instant switching back.") },
                    )
                }
                selectedHit?.let { hit ->
                    val current = runtime
                    val currentDeps = current?.let { deps[it] }
                    if (currentDeps != null) {
                        ResultDetailScreen(
                            hit = hit,
                            query = currentDeps.searchViewModel.uiState.collectAsState().value.query,
                            onBack = { selectedHit = null },
                        )
                    }
                }
                if (showStats) {
                    ModalBottomSheet(onDismissRequest = { showStats = false }) {
                        val current = runtime
                        val currentDeps = current?.let { deps[it] }
                        if (currentDeps != null) {
                            val ingest by currentDeps.ingestViewModel.uiState.collectAsState()
                            val search by currentDeps.searchViewModel.uiState.collectAsState()
                            StatsSheetContent(
                                data = StatsReportData(
                                    device = systemStats.snapshot(),
                                    modelName = currentDeps.modelName,
                                    modelLoadMs = currentDeps.modelLoadMs,
                                    corpus = ingest.corpus,
                                    documents = ingest.documents,
                                    lastRun = ingest.lastRun,
                                    lastQuery = search.queryMs?.let {
                                        QueryStats(search.query, search.hits.size, it)
                                    },
                                ),
                                onExport = { format -> exportStats(format, currentDeps) },
                            )
                        }
                    }
                }
            }
        }
        maybeRunBenchmark()
    }

    /**
     * Sustained embedding workload for power measurement:
     * `am start … --es onnx_ep cpu|webgpu --ei bench_seconds 120`.
     *
     * Runs the real `Embedder` in a tight loop (no database writes, no UI taps) for
     * the requested duration, keeping the screen on, then logs chunks and ms/chunk.
     * A single 6-chunk ingest lasts ~0.1 s — far below the battery counters'
     * resolution — so power runs need a load that lasts minutes.
     */
    private fun maybeRunBenchmark() {
        val seconds = intent.getIntExtra("bench_seconds", 0)
        if (seconds <= 0) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.Default) { runtimeManager.select(InferenceRuntime.ONNX) }
                .getOrElse { error ->
                    Log.e(TAG, "MyLeo-Bench: model load failed: ${error.message}")
                    return@launch
                }
            Log.i(TAG, "MyLeo-Bench: start ${seconds}s · ${loaded.modelName} · load ${loaded.modelLoadMs}ms")
            val embedder = loaded.embedder
            val started = SystemClock.elapsedRealtime()
            var chunks = 0
            withContext(Dispatchers.Default) {
                while (SystemClock.elapsedRealtime() - started < seconds * 1000L) {
                    val t0 = SystemClock.elapsedRealtime()
                    embedder.embed(BENCH_TEXTS[chunks % BENCH_TEXTS.size])
                    val dt = SystemClock.elapsedRealtime() - t0
                    if (chunks < 5) Log.i(TAG, "MyLeo-Bench: chunk $chunks took ${dt}ms")
                    chunks++
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - started
            Log.i(
                TAG,
                "MyLeo-Bench: embedded $chunks chunks in ${elapsed}ms " +
                    "(${String.format(Locale.US, "%.2f", elapsed.toDouble() / chunks)} ms/chunk)",
            )
        }
    }

    private fun switchRuntime(target: InferenceRuntime, done: (Boolean) -> Unit) {        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { runtimeManager.select(target) }
            result
                .onSuccess { loaded ->
                    onRuntimeLoaded(target, loaded)
                    done(true)
                }
                .onFailure { error ->
                    Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
                    done(false)
                }
        }
    }

    private fun exportStats(format: StatsFormat, currentDeps: RuntimeDeps) {
        val ingest = currentDeps.ingestViewModel.uiState.value
        val search = currentDeps.searchViewModel.uiState.value
        val generatedAt = Instant.now().toString()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val (fileName, mimeType, content) = when (format) {
            StatsFormat.MARKDOWN -> Triple(
                "myleo-stats-$stamp.md",
                "text/markdown",
                buildStatsMarkdown(APP_LABEL, reportData(currentDeps), generatedAt),
            )
            StatsFormat.JSON -> Triple(
                "myleo-stats-$stamp.json",
                "application/json",
                buildStatsJson(APP_LABEL, reportData(currentDeps), generatedAt),
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

    private fun reportData(currentDeps: RuntimeDeps): StatsReportData {
        val ingest = currentDeps.ingestViewModel.uiState.value
        val search = currentDeps.searchViewModel.uiState.value
        return StatsReportData(
            device = systemStats.snapshot(),
            modelName = currentDeps.modelName,
            modelLoadMs = currentDeps.modelLoadMs,
            corpus = ingest.corpus,
            documents = ingest.documents,
            lastRun = ingest.lastRun,
            lastQuery = search.queryMs?.let { QueryStats(search.query, search.hits.size, it) },
        )
    }

    companion object {
        private const val TAG = "MyLeo-Model"
        private const val APP_LABEL = "MyLeo VS Dual"
    }
}

/** Fixed, varied inputs for the sustained benchmark loop (same list for every EP). */
private val BENCH_TEXTS = listOf(
    "The refrigerator keeps food cold by moving heat from inside the cabinet to the room.",
    "A compressor circulates refrigerant through the coils and the expansion valve drops its pressure.",
    "The thermostat cycles the compressor on and off to hold the set temperature.",
    "Freezers reach about minus eighteen degrees Celsius to preserve food for months.",
    "A heat pump moves thermal energy in the opposite direction to a fridge.",
    "Insulation slows heat transfer so the cabinet stays cold with less work.",
    "The defrost cycle briefly warms the evaporator to melt accumulated frost.",
    "A dirty condenser coil makes the fridge run longer and use more energy.",
)
