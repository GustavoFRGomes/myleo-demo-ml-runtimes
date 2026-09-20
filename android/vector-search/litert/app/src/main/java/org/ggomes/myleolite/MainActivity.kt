package org.ggomes.myleolite

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
import org.ggomes.myleolite.data.DefaultEmbeddingRepository
import org.ggomes.myleolite.data.LiteRtEmbedderFactory
import org.ggomes.myleolite.data.MissingModelEmbedder
import org.ggomes.myleolite.domain.CalculateEmbeddingUseCase
import org.ggomes.myleolite.ui.AppScreen
import org.ggomes.myleolite.ui.ingest.IngestScreen
import org.ggomes.myleolite.ui.ingest.IngestViewModel
import org.ggomes.myleolite.ui.search.SearchScreen
import org.ggomes.myleolite.ui.search.SearchViewModel
import org.ggomes.myleolite.ui.theme.MyLeoLiteTheme

class MainActivity : ComponentActivity() {

    private val useCase: CalculateEmbeddingUseCase by lazy {
        val embedder = try {
            LiteRtEmbedderFactory.create(applicationContext)
        } catch (e: Exception) {
            MissingModelEmbedder(e.message ?: "model assets missing")
        }
        CalculateEmbeddingUseCase(DefaultEmbeddingRepository(embedder))
    }

    private val vmFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
                modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                    SearchViewModel(useCase)
                modelClass.isAssignableFrom(IngestViewModel::class.java) ->
                    IngestViewModel(useCase)
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
}
