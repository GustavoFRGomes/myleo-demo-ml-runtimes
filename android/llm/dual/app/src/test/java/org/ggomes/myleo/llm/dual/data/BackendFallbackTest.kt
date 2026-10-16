package org.ggomes.myleo.llm.dual.data

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class BackendFallbackTest {

    @Test
    fun firstWorkingBackend_firstSucceeds_returnsItAndDoesNotFallBack() {
        val tried = mutableListOf<String>()

        val (result, backend) = firstWorkingBackend(listOf("gpu", "cpu")) { b ->
            tried += b
            "engine-$b"
        }

        assertThat(result).isEqualTo("engine-gpu")
        assertThat(backend).isEqualTo("gpu")
        assertThat(tried).containsExactly("gpu")
    }

    @Test
    fun firstWorkingBackend_firstFails_fallsBackToSecond() {
        val result = firstWorkingBackend(listOf("gpu", "cpu")) { b ->
            if (b == "gpu") throw IllegalStateException("OpenCL library not found") else "engine-cpu"
        }

        assertThat(result.first).isEqualTo("engine-cpu")
        assertThat(result.second).isEqualTo("cpu")
    }

    @Test
    fun firstWorkingBackend_reportsEachFailureInOrder() {
        val failures = mutableListOf<String>()

        firstWorkingBackend(
            backends = listOf("gpu", "npu", "cpu"),
            onFailure = { b, _ -> failures += b },
        ) { b ->
            if (b == "cpu") "engine-cpu" else throw RuntimeException("no $b")
        }

        assertThat(failures).containsExactly("gpu", "npu")
    }

    @Test
    fun firstWorkingBackend_allFail_throwsLastError() {
        val last = IllegalStateException("cpu also failed")

        assertThatThrownBy {
            firstWorkingBackend(listOf("gpu", "cpu")) { b ->
                if (b == "cpu") throw last else throw RuntimeException("no gpu")
            }
        }.isSameAs(last)
    }

    @Test
    fun firstWorkingBackend_noBackends_throwsIllegalState() {
        assertThatThrownBy { firstWorkingBackend(emptyList<String>()) { "unreachable" } }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
