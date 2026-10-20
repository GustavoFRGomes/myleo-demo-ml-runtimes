package org.ggomes.myleo.llm.onnxgenai.data

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Repairs the one ORTX-incompatible regex in MiniCPM5-1B's `tokenizer.json`.
 *
 * ONNX Runtime GenAI delegates BPE pre-tokenization to ORTX, which compiles every
 * `Split` regex against a fixed menu of alternation branches
 * (`operators/tokenizer/bpe_utils.hpp`). MiniCPM5's second `Split` contains
 * `\p{N}+`, which is not in that menu, so the leftover is compiled with
 * `std::regex` and the model refuses to load with `Invalid regex: \p{N}+`.
 * Replacing `+` with the equivalent bounded `{1,3}` (+4 bytes on disk) keeps both
 * `Split` steps and every token id intact — verified against the pristine HF
 * tokenizer over 31 realistic and 20,000 fuzzed inputs.
 *
 * The file stores the pattern JSON-escaped, so the anchor is the two-backslash
 * byte sequence `\\p{N}+`. Idempotent: a file without the anchor is left untouched.
 */
internal object TokenizerPatch {
    /** JSON-escaped form exactly as it appears on disk. */
    internal const val UNSUPPORTED = """\\p{N}+"""
    internal const val SUPPORTED = """\\p{N}{1,3}"""

    enum class Result {
        /** The anchor was present and has been rewritten. */
        PATCHED,

        /** No anchor left — already patched, or an export that never had it. */
        ALREADY_PATCHED,
    }

    fun apply(tokenizerFile: File): Result {
        require(tokenizerFile.isFile) { "tokenizer.json missing: ${tokenizerFile.path}" }

        val original = tokenizerFile.readBytes()
        val anchor = UNSUPPORTED.toByteArray(Charsets.UTF_8)
        val replacement = SUPPORTED.toByteArray(Charsets.UTF_8)

        if (original.indexOfPattern(anchor, 0) < 0) return Result.ALREADY_PATCHED

        val out = ByteArrayOutputStream(original.size + replacement.size * 2)
        var cursor = 0
        while (true) {
            val at = original.indexOfPattern(anchor, cursor)
            if (at < 0) break
            out.write(original, cursor, at - cursor)
            out.write(replacement)
            cursor = at + anchor.size
        }
        out.write(original, cursor, original.size - cursor)

        val temp = File(tokenizerFile.parentFile, "${tokenizerFile.name}.patched")
        temp.writeBytes(out.toByteArray())
        check(!tokenizerFile.exists() || tokenizerFile.delete()) {
            "could not replace ${tokenizerFile.path}"
        }
        check(temp.renameTo(tokenizerFile)) {
            temp.delete()
            "could not finalise ${tokenizerFile.path}"
        }
        return Result.PATCHED
    }

    private fun ByteArray.indexOfPattern(pattern: ByteArray, from: Int): Int {
        if (pattern.isEmpty()) return from
        val last = size - pattern.size
        var start = from
        outer@ while (start <= last) {
            for (offset in pattern.indices) {
                if (this[start + offset] != pattern[offset]) {
                    start++
                    continue@outer
                }
            }
            return start
        }
        return -1
    }
}
