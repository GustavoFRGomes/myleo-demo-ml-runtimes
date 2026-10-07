package org.ggomes.myleo.llm.dual.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** [ModelDownloader] for a single file over `HttpURLConnection`, streaming to `.part`. */
class HttpFileDownloader(
    private val url: String,
    private val destination: File,
) : ModelDownloader {
    override suspend fun download(
        onProgress: (received: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
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
            val total = connection.contentLengthLong
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
                        onProgress(received, total)
                    }
                }
            }
            if (destination.exists()) destination.delete()
            check(temp.renameTo(destination)) { "could not finalize download" }
            destination
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
