package org.ggomes.myleo.vectorsearch.dual.domain

/**
 * Splits raw corpus text into chunks (F-VS-1): paragraphs separated by blank
 * lines, trimmed, empties dropped. Same rules must apply in every variation.
 */
fun chunkCorpus(text: String): List<String> =
    text.split(Regex("(\\r?\\n)\\s*(\\r?\\n)"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
