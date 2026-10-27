package org.ggomes.myleo.decision.dual.data

import android.content.Context
import java.io.File

/** The resolved LiteRT package: tokenizer, token table and whichever graphs are installed. */
data class LiteRtFiles(
    val directory: File,
    val tokenizer: File,
    val tokenTable: File,
    val graphs: Map<Int, File>,
) {
    fun graph(window: Int): File? = graphs[window]

    val windows: List<Int> get() = graphs.keys.sorted()
}

/**
 * File names, sizes and Hugging Face delivery for `litert-community/Julia-1-LiteRT`
 * (revision `8b3733e196c201ee32e8f5063211588c7f87dd7e`). Models are never committed; each
 * runtime keeps its own folder under `filesDir`, so switching leaves the other download intact.
 */
object LiteRtModelLocator {
    const val FOLDER = "julia-1-litert"
    const val REPO = "litert-community/Julia-1-LiteRT"

    const val TOKENIZER_FILE = "tokenizer.json"
    const val TOKEN_TABLE_FILE = "julia1_token_table_fp16.bin"
    const val GRAPH_512_FILE = "julia1_s512_fp32.tflite"
    const val GRAPH_1024_FILE = "julia1_s1024_fp32.tflite"

    const val TOKENIZER_BYTES = 34_363_188L
    const val TOKEN_TABLE_BYTES = 196_608_000L
    const val GRAPH_512_BYTES = 185_095_348L
    const val GRAPH_1024_BYTES = 188_503_220L

    /** The smallest usable package: 512-token graph + table + tokenizer (416,066,536 bytes). */
    val REQUIRED: List<RemoteFile> =
        listOf(
            RemoteFile(TOKENIZER_FILE, TOKENIZER_BYTES, REPO),
            RemoteFile(TOKEN_TABLE_FILE, TOKEN_TABLE_BYTES, REPO),
            RemoteFile(GRAPH_512_FILE, GRAPH_512_BYTES, REPO),
        )

    /** One tap fetches both windows (604,569,756 bytes); S1024 is optional at runtime. */
    val DOWNLOAD: List<RemoteFile> = REQUIRED + RemoteFile(GRAPH_1024_FILE, GRAPH_1024_BYTES, REPO)

    val HINT: String =
        "Julia-1 LiteRT is not downloaded yet — fetch ~604 MB (both graphs, token table, " +
            "tokenizer) from Hugging Face, or push the files into files/models/$FOLDER/."

    fun directory(context: Context): File = File(context.filesDir, "models/$FOLDER")

    fun graphFileName(window: Int): String = "julia1_s${window}_fp32.tflite"

    fun locate(directory: File): LiteRtFiles? {
        if (!REQUIRED.all { File(directory, it.name).isFile }) return null
        val graphs = linkedMapOf<Int, File>()
        listOf(512 to GRAPH_512_FILE, 1024 to GRAPH_1024_FILE).forEach { (window, name) ->
            val file = File(directory, name)
            if (file.isFile) graphs[window] = file
        }
        return LiteRtFiles(
            directory = directory,
            tokenizer = File(directory, TOKENIZER_FILE),
            tokenTable = File(directory, TOKEN_TABLE_FILE),
            graphs = graphs,
        )
    }

    fun locate(context: Context): LiteRtFiles? = locate(directory(context))
}
