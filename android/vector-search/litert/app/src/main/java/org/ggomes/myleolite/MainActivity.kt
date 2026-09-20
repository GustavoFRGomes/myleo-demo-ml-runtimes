package org.ggomes.myleolite

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.ggomes.myleolite.data.DefaultEmbeddingRepository
import org.ggomes.myleolite.data.DocumentRepository
import org.ggomes.myleolite.data.LiteRtEmbedderFactory
import org.ggomes.myleolite.data.MissingModelEmbedder
import org.ggomes.myleolite.data.ObjectBoxDocumentRepository
import org.ggomes.myleolite.data.db.ObjectBoxStores
import org.ggomes.myleolite.domain.CalculateEmbeddingUseCase
import org.ggomes.myleolite.domain.Embedder
import org.ggomes.myleolite.ui.AppScreen
import org.ggomes.myleolite.ui.ingest.IngestScreen
import org.ggomes.myleolite.ui.ingest.IngestViewModel
import org.ggomes.myleolite.ui.search.SearchScreen
import org.ggomes.myleolite.ui.search.SearchViewModel
import org.ggomes.myleolite.ui.theme.MyLeoLiteTheme
import kotlin.system.measureTimeMillis

class MainActivity : ComponentActivity() {

    private val useCase: CalculateEmbeddingUseCase by lazy {
        var loadMs: Long? = null
        val embedder = try {
            lateinit var loaded: Embedder
            loadMs = measureTimeMillis {
                loaded = LiteRtEmbedderFactory.create(applicationContext)
            }
            Log.d(TAG, "embedding model loaded in ${loadMs}ms")
            loaded
        } catch (e: Exception) {
            Log.w(TAG, "model assets missing, embeddings will fail: ${e.message}")
            MissingModelEmbedder(e.message ?: "model assets missing")
        }
        modelLoadMs = loadMs
        CalculateEmbeddingUseCase(DefaultEmbeddingRepository(embedder))
    }

    private var modelLoadMs: Long? = null

    private val documentRepository: DocumentRepository by lazy {
        ObjectBoxDocumentRepository(ObjectBoxStores.get(applicationContext))
    }

    private val vmFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
                modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                    SearchViewModel(useCase, documentRepository)
                modelClass.isAssignableFrom(IngestViewModel::class.java) ->
                    IngestViewModel(useCase, documentRepository, modelLoadMs = modelLoadMs)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var screen by rememberSaveable { mutableStateOf(AppScreen.Search) }
            MyLeoLiteTheme {
                when (screen) {
                    AppScreen.Search -> SearchScreen(
                        viewModel = searchViewModel,
                        onOpenIngest = { screen = AppScreen.Ingest },
                    )
                    AppScreen.Ingest -> IngestScreen(
                        viewModel = ingestViewModel,
                        onBack = { screen = AppScreen.Search },
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "MyLeo-Model"
    }
}
