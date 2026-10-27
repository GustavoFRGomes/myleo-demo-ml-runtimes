package org.ggomes.myleo.decision.dual.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/** Writes a report file and returns a human-readable location. A seam for tests. */
fun interface ReportExporter {
    fun export(fileName: String, mimeType: String, content: String): Result<String>
}

/**
 * [ReportExporter] writing to Downloads via MediaStore — no permissions needed for our own files
 * on API 29+. Device-only; the formatting it writes is unit-tested through the pure builders.
 */
class MediaStoreReportExporter(context: Context) : ReportExporter {

    private val appContext = context.applicationContext

    override fun export(fileName: String, mimeType: String, content: String): Result<String> =
        runCatching {
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            val resolver = appContext.contentResolver
            val uri: Uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                    ?: throw IllegalStateException("cannot open $uri")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            "Downloads/$fileName"
        }
}
