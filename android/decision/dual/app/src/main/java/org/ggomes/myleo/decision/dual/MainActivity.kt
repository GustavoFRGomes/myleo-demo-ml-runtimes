package org.ggomes.myleo.decision.dual

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.ggomes.myleo.decision.dual.data.AndroidSystemStatsProvider
import org.ggomes.myleo.decision.dual.data.DecisionRuntime
import org.ggomes.myleo.decision.dual.data.HttpFileSetDownloader
import org.ggomes.myleo.decision.dual.data.LiteRtDecider
import org.ggomes.myleo.decision.dual.data.LiteRtGraphRunner
import org.ggomes.myleo.decision.dual.data.LiteRtModelLocator
import org.ggomes.myleo.decision.dual.data.MediaStoreReportExporter
import org.ggomes.myleo.decision.dual.data.OnnxDecider
import org.ggomes.myleo.decision.dual.data.OnnxGraphRunner
import org.ggomes.myleo.decision.dual.data.OnnxModelLocator
import org.ggomes.myleo.decision.dual.data.RuntimeBinding
import org.ggomes.myleo.decision.dual.ui.decision.DecisionScreen
import org.ggomes.myleo.decision.dual.ui.decision.DecisionViewModel
import org.ggomes.myleo.decision.dual.ui.theme.MyLeoDecisionDualTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, Factory(applicationContext))[DecisionViewModel::class.java]
        setContent {
            MyLeoDecisionDualTheme {
                DecisionScreen(viewModel)
            }
        }
    }

    /** Wires both runtime lanes: locator + engine factory + HTTP downloader per picker option. */
    private class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DecisionViewModel(
                bindings =
                    mapOf(
                        DecisionRuntime.LITERT to
                            RuntimeBinding(
                                runtime = DecisionRuntime.LITERT,
                                hint = LiteRtModelLocator.HINT,
                                createDecider = {
                                    LiteRtModelLocator.locate(context)?.let { files ->
                                        LiteRtDecider(files, LiteRtGraphRunner.factory(context))
                                    }
                                },
                                downloader =
                                    HttpFileSetDownloader(
                                        files = LiteRtModelLocator.DOWNLOAD,
                                        directory = LiteRtModelLocator.directory(context),
                                    ),
                            ),
                        DecisionRuntime.ONNX to
                            RuntimeBinding(
                                runtime = DecisionRuntime.ONNX,
                                hint = OnnxModelLocator.HINT,
                                createDecider = {
                                    OnnxModelLocator.locate(context)?.let { files ->
                                        OnnxDecider(files) { model, window ->
                                            OnnxGraphRunner.create(model, window)
                                        }
                                    }
                                },
                                downloader =
                                    HttpFileSetDownloader(
                                        files = OnnxModelLocator.DOWNLOAD,
                                        directory = OnnxModelLocator.directory(context),
                                    ),
                            ),
                    ),
                stats = AndroidSystemStatsProvider(context),
                exporter = MediaStoreReportExporter(context),
            ) as T
    }
}
