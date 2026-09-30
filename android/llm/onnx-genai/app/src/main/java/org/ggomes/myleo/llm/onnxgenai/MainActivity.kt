package org.ggomes.myleo.llm.onnxgenai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.ggomes.myleo.llm.onnxgenai.data.OnnxFileSetDownloader
import org.ggomes.myleo.llm.onnxgenai.data.OnnxGenAIEngine
import org.ggomes.myleo.llm.onnxgenai.data.OnnxGenAIModelLocator
import org.ggomes.myleo.llm.onnxgenai.ui.chat.ChatScreen
import org.ggomes.myleo.llm.onnxgenai.ui.chat.ChatViewModel
import org.ggomes.myleo.llm.onnxgenai.ui.theme.MyLeoLlmOnnxgenaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, Factory(applicationContext))[ChatViewModel::class.java]
        setContent {
            MyLeoLlmOnnxgenaiTheme {
                ChatScreen(viewModel)
            }
        }
    }

    private class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
            locate = { OnnxGenAIModelLocator.locate(context) },
            makeInference = { dir -> OnnxGenAIEngine(dir.absolutePath, OnnxGenAIModelLocator.packageSize(dir)) },
            downloader = OnnxFileSetDownloader(OnnxGenAIModelLocator.packageDir(context)),
        ) as T
    }
}
