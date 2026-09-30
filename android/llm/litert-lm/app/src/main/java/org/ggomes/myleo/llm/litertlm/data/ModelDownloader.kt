package org.ggomes.myleo.llm.litertlm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads a model file, reporting byte progress. Mocked in unit tests. */
interface ModelDownloader {
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (received: Long, total: Long) -> Unit,
    ): File
}

/** [ModelDownloader] over `HttpURLConnection`, streaming to a `.part` file. */
class HttpModelDownloader : ModelDownloader {
    override suspend fun download(
        url: String,
        destination: File,
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
