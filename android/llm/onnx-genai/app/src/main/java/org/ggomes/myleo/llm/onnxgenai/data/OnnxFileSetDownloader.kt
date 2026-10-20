package org.ggomes.myleo.llm.onnxgenai.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** [ModelDownloader] for the multi-file ONNX GenAI package (aggregate byte progress). */
class OnnxFileSetDownloader(
    private val destinationDir: File,
    private val files: List<OnnxGenAIModelLocator.RemoteFile> = OnnxGenAIModelLocator.files,
) : ModelDownloader {

    override suspend fun download(
        onProgress: (received: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destinationDir.mkdirs()
        val totalBytes = files.sumOf { contentLength(OnnxGenAIModelLocator.url(it)) }
        var completedBytes = 0L
        onProgress(completedBytes, totalBytes)

        for (file in files) {
            val destination = File(destinationDir, file.localName)
            if (!destination.exists() || destination.length() == 0L) {
                streamDownload(OnnxGenAIModelLocator.url(file), destination) { received ->
                    onProgress(completedBytes + received, totalBytes)
                }
            }
            completedBytes += destination.length()
            onProgress(completedBytes, totalBytes)
        }
        // Not published upstream; ONNX Runtime GenAI needs it next to the weights.
        MiniCPM5GenAIConfig.ensure(destinationDir)
        destinationDir
    }

    private fun contentLength(url: String): Long = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            connection.contentLengthLong.coerceAtLeast(0)
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(0)

    private suspend fun streamDownload(url: String, destination: File, onProgress: (Long) -> Unit) {
        val temp = File(destination.parentFile, "${destination.name}.part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            val code = connection.responseCode
            check(code in 200..299) { "HTTP $code for $url" }
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var received = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        onProgress(received)
                    }
                }
            }
            if (destination.exists()) destination.delete()
            check(temp.renameTo(destination)) { "could not finalize download" }
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
