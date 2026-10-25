package org.ggomes.myleo.decision.dual.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class JuliaInputsTest {

    @Test
    fun pack_padsTheTokenAxisToTheNextMultipleOfEight_andZeroesTheMask() {
        val ids = IntArray(11) { it + 1 }
        val sequence = JuliaSequence(ids = ids, markers = intArrayOf(3, 5), qtype = 0)

        val packed = JuliaInputs.pack(sequence)

        assertThat(packed.length).isEqualTo(16)
        assertThat(packed.inputIds).hasSize(16)
        assertThat(packed.attentionMask.toList())
            .containsExactlyElementsOf(List(11) { 1L } + List(5) { 0L })
        assertThat(packed.inputIds.take(11)).containsExactlyElementsOf(ids.map { it.toLong() })
        assertThat(packed.inputIds.drop(11)).containsOnly(0L)
    }

    @Test
    fun pack_keepsMarkersAndMarksEveryMarkerValid() {
        val sequence = JuliaSequence(ids = IntArray(20) { 7 }, markers = intArrayOf(10, 12, 14), qtype = 1)

        val packed = JuliaInputs.pack(sequence)

        assertThat(packed.markerPositions.toList()).containsExactly(10L, 12L, 14L)
        assertThat(packed.markerMask.toList()).containsExactly(1.toByte(), 1.toByte(), 1.toByte())
        assertThat(packed.markerCount).isEqualTo(3)
    }

    @Test
    fun pack_keepsTheQuestionTypeChannel() {
        val choice = JuliaInputs.pack(JuliaSequence(IntArray(8), intArrayOf(1), qtype = 0))
        val score = JuliaInputs.pack(JuliaSequence(IntArray(8), intArrayOf(1), qtype = 1))
        val noul = JuliaInputs.pack(JuliaSequence(IntArray(8), intArrayOf(1), qtype = 2))

        assertThat(choice.qtype).isEqualTo(0L)
        assertThat(score.qtype).isEqualTo(1L)
        assertThat(noul.qtype).isEqualTo(2L)
    }

    @Test
    fun pack_exactMultipleOfEight_isNotPadded() {
        val packed = JuliaInputs.pack(JuliaSequence(IntArray(8) { 3 }, intArrayOf(0), qtype = 0))

        assertThat(packed.length).isEqualTo(8)
        assertThat(packed.attentionMask).containsOnly(1L)
    }

    @Test
    fun pack_emptyMarkers_yieldAnEmptyMarkerTensor() {
        val packed = JuliaInputs.pack(JuliaSequence(IntArray(8) { 3 }, intArrayOf(), qtype = 0))

        assertThat(packed.markerPositions).isEmpty()
        assertThat(packed.markerMask).isEmpty()
        assertThat(packed.markerCount).isZero()
    }
}
