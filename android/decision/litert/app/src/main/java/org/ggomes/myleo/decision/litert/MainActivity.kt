package org.ggomes.myleo.decision.litert

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.ggomes.myleo.decision.litert.data.HttpFileSetDownloader
import org.ggomes.myleo.decision.litert.data.JuliaEngine
import org.ggomes.myleo.decision.litert.data.JuliaModelLocator
import org.ggomes.myleo.decision.litert.data.LiteRtGraphRunner
import org.ggomes.myleo.decision.litert.ui.decision.DecisionScreen
import org.ggomes.myleo.decision.litert.ui.decision.DecisionViewModel
import org.ggomes.myleo.decision.litert.ui.theme.MyLeoDecisionLitertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, Factory(applicationContext))[DecisionViewModel::class.java]
        setContent {
            MyLeoDecisionLitertTheme {
                DecisionScreen(viewModel)
            }
        }
    }

    /** Wires the real locator, LiteRT graph factory and HTTP package downloader. */
    private class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DecisionViewModel(
                locate = { JuliaModelLocator.locate(context) },
                makeDecider = { files -> JuliaEngine(files, LiteRtGraphRunner.factory(context)) },
                downloader =
                    HttpFileSetDownloader(
                        files = JuliaModelLocator.DOWNLOAD,
                        directory = JuliaModelLocator.directory(context),
                    ),
            ) as T
    }
}
