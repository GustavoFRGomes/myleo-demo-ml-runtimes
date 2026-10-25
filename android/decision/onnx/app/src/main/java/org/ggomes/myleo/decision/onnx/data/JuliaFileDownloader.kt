package org.ggomes.myleo.decision.onnx.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Downloads the model package, reporting aggregate byte progress, and returns its directory. */
interface DecisionModelDownloader {
    suspend fun download(onProgress: (received: Long, total: Long) -> Unit): File
}

/**
 * [DecisionModelDownloader] for the multi-file Julia package over `HttpURLConnection`.
 *
 * Files are fetched in order into `name.part` and renamed on success; a file that already has
 * the expected size is skipped, so a cancelled download resumes where it stopped. The aggregate
 * progress covers the whole package, and a size mismatch fails the download instead of leaving
 * a truncated weights file behind.
 */
class HttpFileSetDownloader(
    private val files: List<RemoteFile>,
    private val directory: File,
) : DecisionModelDownloader {

    init {
        require(files.isNotEmpty()) { "no files to download" }
    }

    override suspend fun download(onProgress: (received: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val total = files.sumOf { it.bytes }
            var completed = 0L
            for (file in files) {
                currentCoroutineContext().ensureActive()
                val destination = File(directory, file.name)
                if (destination.isFile && destination.length() == file.bytes) {
                    completed += file.bytes
                    onProgress(completed, total)
                    continue
                }
                downloadOne(file, destination) { received ->
                    onProgress(completed + received, total)
                }
                completed += file.bytes
            }
            directory
        }

    private suspend fun downloadOne(
        file: RemoteFile,
        destination: File,
        onProgress: (received: Long) -> Unit,
    ) {
        val temp = File(destination.parentFile, "${destination.name}.part")
        val connection = (URL(file.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for ${file.url}")
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
            if (temp.length() != file.bytes) {
                throw IOException(
                    "short download for ${file.name}: ${temp.length()} B, expected ${file.bytes} B",
                )
            }
            if (destination.exists()) destination.delete()
            check(temp.renameTo(destination)) { "could not finalize ${file.name}" }
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
