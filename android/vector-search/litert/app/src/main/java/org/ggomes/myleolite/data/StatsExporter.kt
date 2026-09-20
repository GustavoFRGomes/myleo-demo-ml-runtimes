package org.ggomes.myleolite.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * Writes report files to Downloads via MediaStore (no permissions needed on
 * API 29+ for our own files). Device-only — not unit-tested.
 */
class StatsExporter(context: Context) {

    private val appContext = context.applicationContext

    fun export(fileName: String, mimeType: String, content: String): Result<Uri> = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = appContext.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: throw IllegalStateException("cannot open $uri")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        uri
    }
}
