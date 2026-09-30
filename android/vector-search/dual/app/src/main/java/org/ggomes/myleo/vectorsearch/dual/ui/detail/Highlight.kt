package org.ggomes.myleo.vectorsearch.dual.ui.detail

/**
 * Highlights case-insensitive occurrences of the query's words in [text].
 * Returns the matching ranges plus the match count. Pure — unit-tested.
 */
fun highlightMatches(text: String, query: String): Pair<List<IntRange>, Int> {
    val terms = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList<IntRange>() to 0
    val pattern = Regex(terms.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    val ranges = pattern.findAll(text).map { it.range }.toList()
    return ranges to ranges.size
}
