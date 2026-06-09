package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure tests for the ABI × selection narrowing advisory (#81): the group-aware gate and message.
 */
class AbiNarrowingAdvisoryTest {

    @Test
    fun `given a same-ABI-group sibling is registered when computing uncovered then the sibling is not flagged`() {
        // The headline case: iosArm64 + iosSimulatorArm64 supported, only the simulator registered.
        // The iOS ABI surface is still dumped/validated by the simulator, so iosArm64 must NOT
        // warn.
        val supported =
            KmpTargetSet.of(
                KmpTarget.Native.Apple.Ios.Arm64,
                KmpTarget.Native.Apple.Ios.SimulatorArm64,
            )
        val registered = KmpTargetSet.of(KmpTarget.Native.Apple.Ios.SimulatorArm64)
        assertEquals(emptyList(), abiUncoveredGroups(supported, registered))
    }

    @Test
    fun `given a family with no registered member when computing uncovered then it is flagged`() {
        // jvm registered; the linux and web ABI groups have no representative → both flagged.
        val supported =
            KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Linux.X64, KmpTarget.Web.Js)
        val registered = KmpTargetSet.of(KmpTarget.Jvm.Desktop)
        assertEquals(listOf("js", "linuxX64"), abiUncoveredGroups(supported, registered))
    }

    @Test
    fun `given one apple family covered and another not when computing uncovered then only the uncovered family is flagged`() {
        // iosArm64 registered covers the iOS group; macosArm64 has no representative → flagged.
        val supported =
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64, KmpTarget.Native.Apple.Macos.Arm64)
        val registered = KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64)
        assertEquals(listOf("macosArm64"), abiUncoveredGroups(supported, registered))
    }

    @Test
    fun `given the full selection when computing uncovered then nothing is flagged`() {
        val all = KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Linux.X64)
        assertEquals(emptyList(), abiUncoveredGroups(all, all))
    }

    @Test
    fun `given jvm and androidTarget when computing groups then they are distinct ABIs`() {
        // Both are JVM-family for the metadata trap (#72), but their ABIs differ — distinct groups.
        assertEquals("jvm", abiGroupOf(KmpTarget.Jvm.Desktop))
        assertEquals("androidTarget", abiGroupOf(KmpTarget.Jvm.Android))
        // ...while iOS device and simulator share one ABI group.
        assertEquals(
            abiGroupOf(KmpTarget.Native.Apple.Ios.Arm64),
            abiGroupOf(KmpTarget.Native.Apple.Ios.SimulatorArm64),
        )
    }

    @Test
    fun `given uncovered targets when the warning renders then it names the path the task and the targets`() {
        val message = abiNarrowingWarning(":lib", "apiCheck", listOf("js", "linuxX64"))
        assertTrue(":lib" in message, message)
        assertTrue("apiCheck" in message, message)
        assertTrue("js, linuxX64" in message, message)
    }

    @Test
    fun `given the advisory when it renders then it points at the full-selection CI lane and strict mode`() {
        val message = abiNarrowingWarning(":lib", "apiDump", listOf("linuxX64"))
        assertTrue("full selection" in message, message)
        assertTrue("CI" in message, message)
        assertTrue("strict" in message, message)
        // It must never claim to be the safety net itself — it points at the lane that is.
        assertFalse("guarantees" in message, message)
    }
}
