package org.ggomes.myleo.llm.litertlm

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.ggomes.myleo.llm.litertlm.data.HttpModelDownloader
import org.ggomes.myleo.llm.litertlm.data.LiteRTLMEngine
import org.ggomes.myleo.llm.litertlm.data.LiteRTLMModelLocator
import org.ggomes.myleo.llm.litertlm.ui.chat.ChatScreen
import org.ggomes.myleo.llm.litertlm.ui.chat.ChatViewModel
import org.ggomes.myleo.llm.litertlm.ui.theme.MyLeoLlmLitertlmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this, Factory(applicationContext))[ChatViewModel::class.java]
        setContent {
            MyLeoLlmLitertlmTheme {
                ChatScreen(viewModel)
            }
        }
    }

    private class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(
            locate = { LiteRTLMModelLocator.locate(context) },
            makeInference = { file -> LiteRTLMEngine(file.absolutePath) },
            downloader = HttpModelDownloader(),
            remoteUrl = LiteRTLMModelLocator.remoteUrl(),
            downloadTarget = { LiteRTLMModelLocator.modelFile(context) },
        ) as T
    }
}
