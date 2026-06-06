package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure decision/message facts of the inert-module advisory (#71): a module that declared `supports
 * { }` but registered zero targets is inert — KGP still materializes its commonMain metadata
 * compilation, which fails with no platform targets. The predicate is cause-agnostic (disjoint
 * overlap, explicitly-empty selection, android-only-without-AGP all flag identically) and host-free
 * (registration is host-blind, so a registered-but-uncompilable module is NOT inert). No Gradle
 * here; the wiring (warnOrFail + dedup + ordering) is proven by [InertModuleAdvisoryInProcessTest].
 */
class InertModuleAdvisoryTest {

    private val jvm = KmpTarget.Jvm.Desktop
    private val iosArm64 = KmpTarget.Native.Apple.Ios.Arm64
    private val wasmJs = KmpTarget.Web.WasmJs

    @Test
    fun `given supports declared and zero registrations when the policy is computed then it flags`() {
        assertTrue(shouldWarnInertModule(supportsDeclared = true, registered = KmpTargetSet.empty))
    }

    @Test
    fun `given supports declared and a registered leaf when the policy is computed then it does not flag`() {
        // Also the host-blindness fact: registration is host-independent, so a registered leaf the
        // current host cannot compile still counts — the module is not inert, on any host.
        assertFalse(
            shouldWarnInertModule(supportsDeclared = true, registered = KmpTargetSet.of(iosArm64))
        )
    }

    @Test
    fun `given supports never declared when the policy is computed then it does not flag`() {
        // Explicit-selection doctrine: a module that never calls supports { } intentionally
        // registers nothing — that is not the inert trap.
        assertFalse(
            shouldWarnInertModule(supportsDeclared = false, registered = KmpTargetSet.empty)
        )
    }

    @Test
    fun `given a disjoint non-empty selection when both policies are computed then empty-overlap and inert both flag`() {
        // The intended composition: empty-overlap diagnoses the cause (selection vs supported
        // mismatch), inert names the consequence (doomed metadata compilation). Both fire.
        val selection = KmpTargetSet.of(wasmJs)
        val supported = KmpTargetSet.of(jvm)
        assertTrue(shouldWarnEmptyOverlap(selection, supported))
        assertTrue(shouldWarnInertModule(supportsDeclared = true, registered = KmpTargetSet.empty))
    }

    @Test
    fun `given an explicitly empty selection when both policies are computed then only inert flags`() {
        // The coverage gap inert closes: empty-overlap is silent on an empty selection by design
        // (issue #9 — "build nothing" is not a mismatch), but the doomed metadata task exists
        // regardless of intent, so inert still signals it.
        assertFalse(shouldWarnEmptyOverlap(KmpTargetSet.empty, KmpTargetSet.of(jvm)))
        assertTrue(shouldWarnInertModule(supportsDeclared = true, registered = KmpTargetSet.empty))
    }

    @Test
    fun `given a module path when the warning is built then it names the inert state the consequence and the gate`() {
        val message = inertModuleWarning(":shared")
        assertContains(message, ":shared")
        assertContains(message, "supports { }")
        assertContains(message, "inert")
        assertContains(message, "commonMain metadata compilation")
        assertContains(message, "registered().isEmpty()")
        // Deliberately set-free: the message is cause-agnostic (it fires for a disjoint overlap, an
        // explicitly-empty selection, or an android-only AGP skip alike), so naming the selection
        // or
        // supported sets would mislead in at least one of those cases.
        assertFalse("[" in message, message)
    }
}
