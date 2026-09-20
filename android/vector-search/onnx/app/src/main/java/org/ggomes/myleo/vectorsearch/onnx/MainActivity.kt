package org.ggomes.myleo.vectorsearch.onnx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.ggomes.myleo.vectorsearch.onnx.data.DefaultEmbeddingRepository
import org.ggomes.myleo.vectorsearch.onnx.data.DocumentRepository
import org.ggomes.myleo.vectorsearch.onnx.data.MissingModelEmbedder
import org.ggomes.myleo.vectorsearch.onnx.data.ObjectBoxDocumentRepository
import org.ggomes.myleo.vectorsearch.onnx.data.db.ObjectBoxStores
import org.ggomes.myleo.vectorsearch.onnx.domain.CalculateEmbeddingUseCase
import org.ggomes.myleo.vectorsearch.onnx.ui.AppScreen
import org.ggomes.myleo.vectorsearch.onnx.ui.ingest.IngestScreen
import org.ggomes.myleo.vectorsearch.onnx.ui.ingest.IngestViewModel
import org.ggomes.myleo.vectorsearch.onnx.ui.search.SearchScreen
import org.ggomes.myleo.vectorsearch.onnx.ui.search.SearchViewModel
import org.ggomes.myleo.vectorsearch.onnx.ui.theme.MyLeoVsOnnxTheme

class MainActivity : ComponentActivity() {

    private val useCase: CalculateEmbeddingUseCase by lazy {
        // No ONNX embedder yet: the missing-model placeholder keeps the app
        // runnable and its error state demoable until one lands.
        CalculateEmbeddingUseCase(
            DefaultEmbeddingRepository(MissingModelEmbedder()),
        )
    }

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
                    IngestViewModel(useCase, documentRepository)
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
            MyLeoVsOnnxTheme {
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
}
