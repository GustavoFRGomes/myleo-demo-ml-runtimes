package org.ggomes.myleo.llm.dual

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.ggomes.myleo.llm.dual.data.AndroidGpuPolicy
import org.ggomes.myleo.llm.dual.data.HttpFileDownloader
import org.ggomes.myleo.llm.dual.data.LiteRTLMEngine
import org.ggomes.myleo.llm.dual.data.LiteRTLMModelLocator
import org.ggomes.myleo.llm.dual.data.OnnxFileSetDownloader
import org.ggomes.myleo.llm.dual.data.OnnxGenAIEngine
import org.ggomes.myleo.llm.dual.data.OnnxGenAIModelLocator
import org.ggomes.myleo.llm.dual.data.RuntimeBinding
import org.ggomes.myleo.llm.dual.data.RuntimeOption
import org.ggomes.myleo.llm.dual.ui.chat.ChatScreen
import org.ggomes.myleo.llm.dual.ui.chat.ChatViewModel
import org.ggomes.myleo.llm.dual.ui.theme.MyLeoLlmDualTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, Factory(applicationContext))[ChatViewModel::class.java]
        setContent {
            MyLeoLlmDualTheme {
                ChatScreen(viewModel)
            }
        }
    }

    private class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
            bindings = mapOf(
                RuntimeOption.LITERT_LM to RuntimeBinding(
                    option = RuntimeOption.LITERT_LM,
                    hint = LiteRTLMModelLocator.HINT,
                    locate = { LiteRTLMModelLocator.locate(context) },
                    makeInference = { file -> LiteRTLMEngine(file.absolutePath, AndroidGpuPolicy(context)) },
                    downloader = HttpFileDownloader(
                        LiteRTLMModelLocator.remoteUrl(),
                        LiteRTLMModelLocator.modelFile(context),
                    ),
                ),
                RuntimeOption.ONNX_GENAI to RuntimeBinding(
                    option = RuntimeOption.ONNX_GENAI,
                    hint = OnnxGenAIModelLocator.HINT,
                    locate = { OnnxGenAIModelLocator.locate(context) },
                    makeInference = { dir ->
                        OnnxGenAIEngine(dir.absolutePath, OnnxGenAIModelLocator.packageSize(dir))
                    },
                    downloader = OnnxFileSetDownloader(OnnxGenAIModelLocator.packageDir(context)),
                ),
            ),
        ) as T
    }
}
