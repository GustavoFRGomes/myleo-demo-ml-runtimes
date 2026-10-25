package org.ggomes.myleo.decision.onnx

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.ggomes.myleo.decision.onnx.data.HttpFileSetDownloader
import org.ggomes.myleo.decision.onnx.data.JuliaEngine
import org.ggomes.myleo.decision.onnx.data.JuliaModelLocator
import org.ggomes.myleo.decision.onnx.data.OnnxGraphRunner
import org.ggomes.myleo.decision.onnx.ui.decision.DecisionScreen
import org.ggomes.myleo.decision.onnx.ui.decision.DecisionViewModel
import org.ggomes.myleo.decision.onnx.ui.theme.MyLeoDecisionOnnxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, Factory(applicationContext))[DecisionViewModel::class.java]
        setContent {
            MyLeoDecisionOnnxTheme {
                DecisionScreen(viewModel)
            }
        }
    }

    /** Wires the real locator, ONNX session factory and HTTP package downloader. */
    private class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DecisionViewModel(
                locate = { JuliaModelLocator.locate(context) },
                makeDecider = { files ->
                    JuliaEngine(files) { model, window -> OnnxGraphRunner.create(model, window) }
                },
                downloader =
                    HttpFileSetDownloader(
                        files = JuliaModelLocator.DOWNLOAD,
                        directory = JuliaModelLocator.directory(context),
                    ),
            ) as T
    }
}
