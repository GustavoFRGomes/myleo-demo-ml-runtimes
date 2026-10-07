package org.ggomes.myleo.vectorsearch.dual

import org.ggomes.myleo.vectorsearch.dual.ui.detail.highlightMatches
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class HighlightTest {

    @Test
    fun highlightMatches_singleTerm_countsOccurrences() {
        val (ranges, count) = highlightMatches("hello world hello", "hello")
        assertThat(count).isEqualTo(2)
        assertThat(ranges).hasSize(2)
    }

    @Test
    fun highlightMatches_caseInsensitive_matches() {
        val (_, count) = highlightMatches("Hello HELLO", "hello")
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun highlightMatches_multiWord_matchesAnyTerm() {
        val (_, count) = highlightMatches("arnold-j hello world email", "hello world")
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun highlightMatches_blankQuery_returnsZero() {
        val (ranges, count) = highlightMatches("hello", "   ")
        assertThat(count).isEqualTo(0)
        assertThat(ranges).isEmpty()
    }

    @Test
    fun highlightMatches_noMatch_returnsZero() {
        val (ranges, count) = highlightMatches("hello", "xyz")
        assertThat(count).isEqualTo(0)
        assertThat(ranges).isEmpty()
    }
}
