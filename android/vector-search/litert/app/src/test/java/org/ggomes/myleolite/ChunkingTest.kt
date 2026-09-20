package org.ggomes.myleolite

import org.ggomes.myleolite.domain.chunkCorpus
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class ChunkingTest {

    @Test
    fun chunkCorpus_blankLineSeparatedParagraphs_splitsAndTrims() {
        val chunks = chunkCorpus("  first  \n\nsecond\n\n\n  third\n")

        assertThat(chunks).containsExactly("first", "second", "third")
    }

    @Test
    fun chunkCorpus_singleBlock_returnsOneChunk() {
        assertThat(chunkCorpus("just one")).containsExactly("just one")
    }

    @Test
    fun chunkCorpus_blankOrEmpty_returnsEmpty() {
        assertThat(chunkCorpus("")).isEmpty()
        assertThat(chunkCorpus("  \n\n  ")).isEmpty()
    }
}
