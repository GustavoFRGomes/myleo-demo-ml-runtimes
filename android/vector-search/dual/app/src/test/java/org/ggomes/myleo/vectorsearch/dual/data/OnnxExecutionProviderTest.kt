package org.ggomes.myleo.vectorsearch.dual.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/** Pure mapping from the launch-extra to the requested ONNX execution provider. */
class OnnxExecutionProviderTest {

    @Test
    fun fromExtra_webgpu_isWebgpu() {
        assertThat(OnnxExecutionProvider.fromExtra("webgpu")).isEqualTo(OnnxExecutionProvider.WEBGPU)
    }

    @Test
    fun fromExtra_mixedCaseAndSpaces_isWebgpu() {
        assertThat(OnnxExecutionProvider.fromExtra(" WebGPU ")).isEqualTo(OnnxExecutionProvider.WEBGPU)
    }

    @Test
    fun fromExtra_null_isCpu() {
        assertThat(OnnxExecutionProvider.fromExtra(null)).isEqualTo(OnnxExecutionProvider.CPU)
    }

    @Test
    fun fromExtra_unknown_isCpu() {
        assertThat(OnnxExecutionProvider.fromExtra("nnapi")).isEqualTo(OnnxExecutionProvider.CPU)
    }
}
