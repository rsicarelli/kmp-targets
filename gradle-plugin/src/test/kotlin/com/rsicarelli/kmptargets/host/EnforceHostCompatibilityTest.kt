package com.rsicarelli.kmptargets.host

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.junit.jupiter.api.Test

/**
 * Strict-mode escalation of the host advisory, tested through the pure seam: the enabled-target set
 * is injected, so every case simulates a host deterministically — no test here may depend on what
 * the machine running the suite can compile (on macOS nothing is host-impossible, which is exactly
 * why the seam exists). Policy stays owned by [impossibleOnHost] / [hostImpossibleWarning]; this
 * suite only proves the warn↔fail severity switch and that both carry the identical message.
 */
class EnforceHostCompatibilityTest {

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

    private val iosArm64 = KmpTarget.Native.Apple.Ios.Arm64
    private val tvosArm64 = KmpTarget.Native.Apple.Tvos.Arm64

    @Test
    fun `given strict on and an impossible target on a simulated linux host when enforced then it fails with the exact warning text`() {
        val ex =
            assertFailsWith<GradleException> {
                enforce(active = KmpTargetSet.of(iosArm64), strict = true)
            }
        assertEquals(
            hostImpossibleWarning(":lib", KonanTarget.LINUX_X64, KmpTargetSet.of(iosArm64)),
            ex.message,
        )
    }

    @Test
    fun `given strict off and an impossible target on a simulated linux host when enforced then it warns with the same text and returns the fresh set`() {
        val warnings = mutableListOf<String>()
        val fresh =
            enforce(active = KmpTargetSet.of(iosArm64), strict = false, warn = warnings::add)
        assertEquals(setOf<KmpTarget>(iosArm64), fresh)
        assertEquals(
            listOf(hostImpossibleWarning(":lib", KonanTarget.LINUX_X64, KmpTargetSet.of(iosArm64))),
            warnings,
        )
    }

    @Test
    fun `given an already warned leaf and a newly impossible leaf when enforced strict then the failure names only the fresh leaf`() {
        val ex =
            assertFailsWith<GradleException> {
                enforce(
                    active = KmpTargetSet.of(iosArm64, tvosArm64),
                    alreadyWarned = setOf(iosArm64),
                    strict = true,
                )
            }
        assertEquals(
            hostImpossibleWarning(":lib", KonanTarget.LINUX_X64, KmpTargetSet.of(tvosArm64)),
            ex.message,
        )
    }

    @Test
    fun `given only already warned leaves when enforced strict then it neither fails nor warns and returns empty`() {
        val warnings = mutableListOf<String>()
        val fresh =
            enforce(
                active = KmpTargetSet.of(iosArm64),
                alreadyWarned = setOf(iosArm64),
                strict = true,
                warn = warnings::add,
            )
        assertEquals(emptySet(), fresh)
        assertTrue(warnings.isEmpty(), "expected no warnings, got: $warnings")
    }

    @Test
    fun `given nothing impossible on a simulated mac host when enforced strict then it returns empty and never warns or fails`() {
        val warnings = mutableListOf<String>()
        val fresh =
            enforce(
                active = KmpTargetSet.of(iosArm64, tvosArm64),
                enabled = macEnabled,
                strict = true,
                warn = warnings::add,
            )
        assertEquals(emptySet(), fresh)
        assertTrue(warnings.isEmpty(), "expected no warnings, got: $warnings")
    }

    // -- info-task annotation seam (#44) ---------------------------------------------------------

    @Test
    fun `given iosArm64 active on a simulated linux host when impossible ids are computed then it is the only annotation`() {
        assertEquals(
            listOf("iosArm64"),
            hostImpossibleIds(KmpTargetSet.of(iosArm64, KmpTarget.Jvm.Desktop), linuxEnabled),
        )
    }

    @Test
    fun `given the same active set on a simulated mac host when impossible ids are computed then none are annotated`() {
        assertEquals(
            emptyList(),
            hostImpossibleIds(KmpTargetSet.of(iosArm64, tvosArm64), macEnabled),
        )
    }

    @Test
    fun `given several impossible leaves when impossible ids are computed then they are sorted`() {
        assertEquals(
            listOf("iosArm64", "tvosArm64"),
            hostImpossibleIds(KmpTargetSet.of(tvosArm64, iosArm64), linuxEnabled),
        )
    }

    private fun enforce(
        active: KmpTargetSet,
        enabled: Set<KonanTarget> = linuxEnabled,
        alreadyWarned: Set<KmpTarget> = emptySet(),
        strict: Boolean,
        warn: (String) -> Unit = { error("unexpected warning: $it") },
    ): Set<KmpTarget> =
        enforceHostCompatibility(
            path = ":lib",
            active = active,
            enabled = enabled,
            host = KonanTarget.LINUX_X64,
            alreadyWarned = alreadyWarned,
            strict = strict,
            warn = warn,
        )
}
