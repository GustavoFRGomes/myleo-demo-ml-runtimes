package org.ggomes.myleo.decision.litert.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JuliaTokenTableTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun halfToFloat_expandsKnownBitPatterns() {
        assertThat(JuliaTokenTable.halfToFloat(0x3C00)).isEqualTo(1.0f)
        assertThat(JuliaTokenTable.halfToFloat(0x4000)).isEqualTo(2.0f)
        assertThat(JuliaTokenTable.halfToFloat(0x3555)).isCloseTo(0.33325195f, within(1e-7f))
        assertThat(JuliaTokenTable.halfToFloat(0x7BFF)).isEqualTo(65504.0f)
    }

    @Test
    fun halfToFloat_handlesSubnormalsAndSignedZero() {
        assertThat(JuliaTokenTable.halfToFloat(0x0001)).isEqualTo(5.9604645E-8f)
        assertThat(JuliaTokenTable.halfToFloat(0x8000).toRawBits()).isEqualTo((-0.0f).toRawBits())
        assertThat(JuliaTokenTable.halfToFloat(0x0000).toRawBits()).isEqualTo(0)
    }

    @Test
    fun halfToFloat_handlesInfinityAndNaN() {
        assertThat(JuliaTokenTable.halfToFloat(0x7C00)).isEqualTo(Float.POSITIVE_INFINITY)
        assertThat(JuliaTokenTable.halfToFloat(0xFC00)).isEqualTo(Float.NEGATIVE_INFINITY)
        assertThat(JuliaTokenTable.halfToFloat(0x7E00)).isNaN()
    }

    @Test
    fun gather_readsEachRow_andPadsWithThePadRow() {
        val table = JuliaTokenTable(smallTable(), vocabularySize = 4, width = 2)

        val values = table.gather(intArrayOf(1, 2), window = 3)

        assertThat(values.toList()).containsExactly(0.5f, -1.0f, 0.0f, 4.0f, 1.0f, 2.0f)
        table.close()
    }

    @Test
    fun gather_rejectsIdsOutsideTheVocabulary() {
        val table = JuliaTokenTable(smallTable(), vocabularySize = 4, width = 2)

        assertThatThrownBy { table.gather(intArrayOf(4), window = 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        table.close()
    }

    @Test
    fun gather_rejectsASequenceLongerThanTheWindow() {
        val table = JuliaTokenTable(smallTable(), vocabularySize = 4, width = 2)

        assertThatThrownBy { table.gather(intArrayOf(1, 2), window = 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        table.close()
    }

    @Test
    fun init_rejectsAFileOfTheWrongSize() {
        val wrong = temp.newFile("table.bin").apply { writeBytes(ByteArray(10)) }

        assertThatThrownBy { JuliaTokenTable(wrong, vocabularySize = 4, width = 2) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid token table size")
    }

    /** Four rows of width 2: [1, 2], [0.5, -1], [0, 4], [-0.5, 8]. */
    private fun smallTable(): File {
        val bits = intArrayOf(0x3C00, 0x4000, 0x3800, 0xBC00, 0x0000, 0x4400, 0xB800, 0x4800)
        val buffer = ByteBuffer.allocate(bits.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bits.forEach { buffer.putShort(it.toShort()) }
        return temp.newFile("table.bin").apply { writeBytes(buffer.array()) }
    }
}
