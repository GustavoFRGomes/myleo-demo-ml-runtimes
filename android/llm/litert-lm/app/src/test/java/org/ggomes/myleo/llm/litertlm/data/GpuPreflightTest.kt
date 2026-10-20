package org.ggomes.myleo.llm.litertlm.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GpuPreflightTest {

    private val mib = 1024L * 1024
    private val kib = 1024L

    /** The shipped MiniCPM5-1B W4 `_gpu_opt` `.litertlm`. */
    private val shippedBytes = 793_029_040L

    /**
     * Phi-4-mini q8 (~3.9 GB) — the bundle the gate's 2× + headroom shape was
     * calibrated on. Kept as the "large model" case: at 8.8 GiB required it is the
     * one that actually splits the two test devices.
     */
    private val largeModelBytes = 3_910_090_752L

    /** Galaxy Z Fold7 (`SM-F966B`) — GPU loaded fine, ~6.1 GB of it graphics memory. */
    private val fold7RamBytes = 11_379_904L * kib

    /** TCL T807D — 7.6 GB, GPU load OOM-killed the app on every attempt. */
    private val tclRamBytes = 7_684_060L * kib

    @Test
    fun requiredBytes_modelPlusGpuCopyPlusOsHeadroom() {
        assertThat(GpuPreflight.requiredBytes(4L * 1024 * mib))
            .isEqualTo(8L * 1024 * mib + GpuPreflight.OS_HEADROOM_BYTES)
    }

    @Test
    fun requiredBytes_shippedModel_needsAboutThreeGiB() {
        // 2 × 0.739 GiB + 1.5 GiB. Comfortably under both test devices.
        assertThat(GpuPreflight.requiredBytes(shippedBytes))
            .isLessThan(3L * 1024 * mib + 64 * mib)
    }

    @Test
    fun canAttemptGpu_shippedModel_isTrueOnBothTestDevices() {
        assertThat(GpuPreflight.canAttemptGpu(shippedBytes, fold7RamBytes, GpuVerdict.UNKNOWN)).isTrue()
        assertThat(GpuPreflight.canAttemptGpu(shippedBytes, tclRamBytes, GpuVerdict.UNKNOWN)).isTrue()
    }

    @Test
    fun canAttemptGpu_deviceWithAmpleRam_isTrue() {
        assertThat(GpuPreflight.canAttemptGpu(largeModelBytes, fold7RamBytes, GpuVerdict.UNKNOWN)).isTrue()
    }

    @Test
    fun canAttemptGpu_deviceBelowRequiredRam_isFalse() {
        assertThat(GpuPreflight.canAttemptGpu(largeModelBytes, tclRamBytes, GpuVerdict.UNKNOWN)).isFalse()
    }

    @Test
    fun canAttemptGpu_exactlyRequiredRam_isTrue() {
        val exact = GpuPreflight.requiredBytes(largeModelBytes)

        assertThat(GpuPreflight.canAttemptGpu(largeModelBytes, exact, GpuVerdict.UNKNOWN)).isTrue()
        assertThat(GpuPreflight.canAttemptGpu(largeModelBytes, exact - 1, GpuVerdict.UNKNOWN)).isFalse()
    }

    @Test
    fun canAttemptGpu_rememberedBadVerdict_isFalse_evenWithAmpleRam() {
        assertThat(GpuPreflight.canAttemptGpu(largeModelBytes, fold7RamBytes, GpuVerdict.BAD)).isFalse()
    }

    @Test
    fun canAttemptGpu_pendingVerdict_isFalse_becauseItMeansTheRunWasKilled() {
        assertThat(GpuPreflight.canAttemptGpu(largeModelBytes, fold7RamBytes, GpuVerdict.PENDING)).isFalse()
    }

    @Test
    fun canAttemptGpu_okVerdict_isTrue() {
        assertThat(GpuPreflight.canAttemptGpu(largeModelBytes, fold7RamBytes, GpuVerdict.OK)).isTrue()
    }

    @Test
    fun canAttemptGpu_allowPolicy_permitsGpuOnAnyDevice() {
        assertThat(
            GpuPreflight.canAttemptGpu(largeModelBytes, GpuPolicy.Allow.totalRamBytes(), GpuPolicy.Allow.verdict()),
        ).isTrue()
    }

    @Test
    fun resolved_pendingBecomesBad() {
        assertThat(GpuVerdict.PENDING.resolved()).isEqualTo(GpuVerdict.BAD)
    }

    @Test
    fun resolved_otherVerdictsAreUnchanged() {
        assertThat(GpuVerdict.UNKNOWN.resolved()).isEqualTo(GpuVerdict.UNKNOWN)
        assertThat(GpuVerdict.OK.resolved()).isEqualTo(GpuVerdict.OK)
        assertThat(GpuVerdict.BAD.resolved()).isEqualTo(GpuVerdict.BAD)
    }
}
