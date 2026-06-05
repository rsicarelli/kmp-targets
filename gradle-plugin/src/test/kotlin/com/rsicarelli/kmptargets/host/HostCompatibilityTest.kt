package com.rsicarelli.kmptargets.host

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.junit.jupiter.api.Test

/**
 * Pure host-compatibility policy: the decision ([impossibleOnHost]) and message
 * ([hostImpossibleWarning]) take the host's enabled-target set as a parameter, so every test here
 * simulates a host — none depends on the machine actually running the suite.
 */
class HostCompatibilityTest {

    /** A Linux x64 host: Linux, MinGW and Android NDK cross-compile; Apple does not. */
    private val linuxEnabled: Set<KonanTarget> =
        setOf(
            KonanTarget.LINUX_X64,
            KonanTarget.LINUX_ARM64,
            KonanTarget.MINGW_X64,
            KonanTarget.ANDROID_ARM32,
            KonanTarget.ANDROID_ARM64,
            KonanTarget.ANDROID_X86,
            KonanTarget.ANDROID_X64,
        )

    /** A macOS host: every Kotlin/Native target compiles. */
    private val macEnabled: Set<KonanTarget> =
        KmpTargetSet.native.members.mapNotNull(::konanTargetOf).toSet()

    // -- mapping -------------------------------------------------------------------------------

    @Test
    fun `given every leaf when mapped to KonanTarget then native leaves resolve and jvm and web map to null`() {
        val (native, hostAgnostic) = KmpTarget.all.partition { konanTargetOf(it) != null }
        assertEquals(KmpTargetSet.native.members, native.toSet())
        assertEquals(
            KmpTargetSet.jvmFamily.members + KmpTargetSet.web.members,
            hostAgnostic.toSet(),
        )
    }

    @Test
    fun `given the twenty native leaves when mapped then each resolves to a distinct KonanTarget`() {
        val konanTargets = KmpTargetSet.native.members.mapNotNull(::konanTargetOf)
        assertEquals(KmpTargetSet.native.size, konanTargets.toSet().size)
    }

    // -- decision ------------------------------------------------------------------------------

    @Test
    fun `given iosArm64 active when computing impossible on a simulated linux host then iosArm64 is impossible`() {
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
            impossibleOnHost(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), linuxEnabled),
        )
    }

    @Test
    fun `given iosArm64 active when computing impossible on a simulated mac host then nothing is impossible`() {
        assertEquals(
            KmpTargetSet.empty,
            impossibleOnHost(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), macEnabled),
        )
    }

    @Test
    fun `given linuxX64 and iosArm64 active when computing impossible on a simulated linux host then only iosArm64 is named`() {
        val active = KmpTargetSet.of(KmpTarget.Native.Linux.X64, KmpTarget.Native.Apple.Ios.Arm64)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
            impossibleOnHost(active, linuxEnabled),
        )
    }

    @Test
    fun `given only host-agnostic targets active when computing impossible on any simulated host then nothing is impossible`() {
        val active = KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Web.Js, KmpTarget.Web.WasmJs)
        // The empty enabled-set is the harshest possible host — JVM/Web must still never warn.
        assertEquals(KmpTargetSet.empty, impossibleOnHost(active, emptySet()))
    }

    @Test
    fun `given an empty active set when computing impossible on any simulated host then nothing is impossible`() {
        assertEquals(KmpTargetSet.empty, impossibleOnHost(KmpTargetSet.empty, emptySet()))
    }

    // -- determinism guardrail -----------------------------------------------------------------

    @Test
    fun `given the same active set when computing impossible on two simulated hosts then only the impossible subset differs`() {
        val active = KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64, KmpTarget.Native.Linux.X64)
        val onLinux = impossibleOnHost(active, linuxEnabled)
        val onMac = impossibleOnHost(active, macEnabled)
        // The registered set is `active` itself — registration never consumes the host decision,
        // so it is byte-identical across hosts; the advisory subset is the only difference.
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), onLinux)
        assertEquals(KmpTargetSet.empty, onMac)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64, KmpTarget.Native.Linux.X64),
            active,
        )
    }

    // -- dedup across repeated supports calls ----------------------------------------------------

    @Test
    fun `given iosArm64 already warned when a later union adds tvosArm64 impossible then only tvosArm64 is newly warned`() {
        // Mirrors the register(...) dedup: a second `supports { }` call unions in a new impossible
        // leaf after the first already warned — only the fresh leaf is named again.
        val active =
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64, KmpTarget.Native.Apple.Tvos.Arm64)
        val alreadyWarned = setOf<KmpTarget>(KmpTarget.Native.Apple.Ios.Arm64)
        val fresh = impossibleOnHost(active, linuxEnabled).members - alreadyWarned
        assertEquals(setOf<KmpTarget>(KmpTarget.Native.Apple.Tvos.Arm64), fresh)
    }

    // -- message -------------------------------------------------------------------------------

    @Test
    fun `given impossible targets when building the warning then it names the target ids and the host`() {
        val message =
            hostImpossibleWarning(
                path = ":lib",
                host = KonanTarget.LINUX_X64,
                impossible = KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
            )
        assertTrue("iosArm64" in message, "expected target id in: $message")
        assertTrue("LINUX_X64" in message, "expected host in: $message")
        assertTrue(":lib" in message, "expected project path in: $message")
    }

    @Test
    fun `given impossible targets when building the warning then it states they stay registered`() {
        val message =
            hostImpossibleWarning(
                path = ":lib",
                host = KonanTarget.LINUX_X64,
                impossible = KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
            )
        assertTrue("still registered" in message, "expected advisory wording in: $message")
    }

    @Test
    fun `given several impossible targets when building the warning then it names all ids sorted`() {
        val message =
            hostImpossibleWarning(
                path = ":lib",
                host = KonanTarget.LINUX_X64,
                impossible =
                    KmpTargetSet.of(
                        KmpTarget.Native.Apple.Tvos.Arm64,
                        KmpTarget.Native.Apple.Ios.Arm64,
                    ),
            )
        assertTrue("[iosArm64, tvosArm64]" in message, "expected sorted ids in: $message")
    }
}
