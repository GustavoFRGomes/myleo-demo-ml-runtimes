package org.ggomes.myleo.vectorsearch.onnx

import org.ggomes.myleo.vectorsearch.onnx.domain.CsvParser
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class CsvParserTest {

    @Test
    fun parseRows_simple_splitsCells() {
        assertThat(CsvParser.parseRows("a,b,c\n1,2,3"))
            .containsExactly(listOf("a", "b", "c"), listOf("1", "2", "3"))
    }

    @Test
    fun parseRows_quotedComma_staysInField() {
        assertThat(CsvParser.parseRows("a,\"b,c\",d"))
            .containsExactly(listOf("a", "b,c", "d"))
    }

    @Test
    fun parseRows_escapedQuote_unescapes() {
        assertThat(CsvParser.parseRows("\"a\"\"b\",c"))
            .containsExactly(listOf("a\"b", "c"))
    }

    @Test
    fun parseRows_embeddedNewline_staysInField() {
        assertThat(CsvParser.parseRows("a,\"b\nc\",d\n1,2,3"))
            .containsExactly(listOf("a", "b\nc", "d"), listOf("1", "2", "3"))
    }

    @Test
    fun parseRows_trailingNewline_dropsEmptyRow() {
        assertThat(CsvParser.parseRows("a,b\n1,2\n"))
            .containsExactly(listOf("a", "b"), listOf("1", "2"))
    }

    @Test
    fun loadRows_empty_throws() {
        assertThatThrownBy { CsvParser.loadRows("") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun loadRows_maxRows_capsData() {
        val (header, rows) = CsvParser.loadRows("h1,h2\na,b\nc,d\ne,f", maxRows = 2)
        assertThat(header).containsExactly("h1", "h2")
        assertThat(rows).containsExactly(listOf("a", "b"), listOf("c", "d"))
    }
}
