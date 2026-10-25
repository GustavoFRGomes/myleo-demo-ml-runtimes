package org.ggomes.myleo.decision.litert.data

import android.content.Context
import java.io.File

/** One file the app must fetch, with the exact size published in the model repo. */
data class RemoteFile(val name: String, val bytes: Long) {
    val url: String get() = JuliaModelLocator.remoteUrl(name)
}

/** The resolved on-disk package: tokenizer, token table and whichever graphs are installed. */
data class JuliaModelFiles(
    val directory: File,
    val tokenizer: File,
    val tokenTable: File,
    val graphs: Map<Int, File>,
) {
    /** Graph for a static window, or null when that window was not downloaded. */
    fun graph(window: Int): File? = graphs[window]

    /** Installed windows, smallest first. */
    val windows: List<Int> get() = graphs.keys.sorted()
}

/**
 * File names, sizes and Hugging Face delivery for `litert-community/Julia-1-LiteRT`
 * (revision `8b3733e196c201ee32e8f5063211588c7f87dd7e`). Models are never committed; the app
 * downloads them into its own `filesDir`.
 *
 * Sizes are part of the contract, not documentation: a truncated download is rejected instead
 * of producing a silently wrong token table or graph.
 */
object JuliaModelLocator {
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
            RemoteFile(TOKENIZER_FILE, TOKENIZER_BYTES),
            RemoteFile(TOKEN_TABLE_FILE, TOKEN_TABLE_BYTES),
            RemoteFile(GRAPH_512_FILE, GRAPH_512_BYTES),
        )

    /** One tap fetches both windows (604,569,756 bytes); S1024 is optional at runtime. */
    val DOWNLOAD: List<RemoteFile> = REQUIRED + RemoteFile(GRAPH_1024_FILE, GRAPH_1024_BYTES)

    val HINT: String =
        "Julia-1 LiteRT is not downloaded yet — fetch ~604 MB (both graphs, token table, tokenizer) " +
            "from Hugging Face, or push the files into files/models/$FOLDER/."

    fun directory(context: Context): File = File(context.filesDir, "models/$FOLDER")

    fun remoteUrl(name: String): String = "https://huggingface.co/$REPO/resolve/main/$name"

    fun graphFileName(window: Int): String = "julia1_s${window}_fp32.tflite"

    /** Package for a directory when every required file is present, else null. */
    fun locate(directory: File): JuliaModelFiles? {
        if (!REQUIRED.all { File(directory, it.name).isFile }) return null
        val graphs = linkedMapOf<Int, File>()
        listOf(512 to GRAPH_512_FILE, 1024 to GRAPH_1024_FILE).forEach { (window, name) ->
            val file = File(directory, name)
            if (file.isFile) graphs[window] = file
        }
        return JuliaModelFiles(
            directory = directory,
            tokenizer = File(directory, TOKENIZER_FILE),
            tokenTable = File(directory, TOKEN_TABLE_FILE),
            graphs = graphs,
        )
    }

    fun locate(context: Context): JuliaModelFiles? = locate(directory(context))
}
