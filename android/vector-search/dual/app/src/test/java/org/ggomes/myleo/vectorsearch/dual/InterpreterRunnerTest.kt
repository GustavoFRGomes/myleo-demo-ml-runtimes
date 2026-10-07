package org.ggomes.myleo.vectorsearch.dual

import org.ggomes.myleo.vectorsearch.dual.data.resolveInputOrder
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

class InterpreterRunnerTest {

    @Test
    fun resolveInputOrder_namedTensors_mapsIndices() {
        val order = resolveInputOrder(listOf("attention_mask", "token_type_ids", "input_ids"))

        assertThat(order.ids).isEqualTo(2)
        assertThat(order.mask).isEqualTo(0)
        assertThat(order.segment).isEqualTo(1)
    }

    @Test
    fun resolveInputOrder_segmentSynonyms_mapsIndices() {
        val order = resolveInputOrder(listOf("ids", "mask", "segment_ids"))

        assertThat(order).isEqualTo(
            org.ggomes.myleo.vectorsearch.dual.data.InputOrder(ids = 0, mask = 1, segment = 2),
        )
    }

    @Test
    fun resolveInputOrder_tooFewInputs_throws() {
        assertThatThrownBy { resolveInputOrder(listOf("input_ids", "attention_mask")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun resolveInputOrder_unknownNames_throws() {
        assertThatThrownBy { resolveInputOrder(listOf("a", "b", "c")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
