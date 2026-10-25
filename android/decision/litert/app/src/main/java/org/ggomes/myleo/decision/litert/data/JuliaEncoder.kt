package org.ggomes.myleo.decision.litert.data

/**
 * The one thing the sequence builder needs from the tokenizer. A seam so the builder's
 * layout/budget rules are unit-tested with a stub encoder, without a 34 MB tokenizer.json.
 */
interface JuliaEncoder {
    /** Token ids for [text], no special tokens added — exactly how the Julia host encodes. */
    fun encode(text: String): IntArray
}
