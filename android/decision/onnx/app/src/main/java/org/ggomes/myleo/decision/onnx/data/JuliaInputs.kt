package org.ggomes.myleo.decision.onnx.data

/**
 * Packs one strict sequence into the tensors the ONNX graph declares:
 * `input_ids`, `attention_mask`, `marker_pos`, `marker_mask`, `qtype`.
 *
 * Token length is padded up to a multiple of [PAD_MULTIPLE] (the reference browser host does the
 * same), with the attention mask zeroed on the padding. Marker positions are absolute indices
 * into the real tokens, so padding never moves them.
 */
object JuliaInputs {
    /** Token lengths are padded up to this multiple; the graph's token axis is dynamic. */
    const val PAD_MULTIPLE = 8

    class Packed(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val markerPositions: LongArray,
        val markerMask: ByteArray,
        val qtype: Long,
        val length: Int,
        val markerCount: Int,
    )

    fun pack(sequence: JuliaSequence): Packed {
        val length = roundUp(sequence.ids.size, PAD_MULTIPLE)
        val inputIds = LongArray(length)
        val attentionMask = LongArray(length)
        sequence.ids.forEachIndexed { index, id ->
            inputIds[index] = id.toLong()
            attentionMask[index] = 1L
        }
        val markerPositions = LongArray(sequence.markers.size) { sequence.markers[it].toLong() }
        val markerMask = ByteArray(sequence.markers.size) { 1 }
        return Packed(
            inputIds = inputIds,
            attentionMask = attentionMask,
            markerPositions = markerPositions,
            markerMask = markerMask,
            qtype = sequence.qtype.toLong(),
            length = length,
            markerCount = sequence.markers.size,
        )
    }

    private fun roundUp(value: Int, multiple: Int): Int =
        ((value + multiple - 1) / multiple) * multiple
}
