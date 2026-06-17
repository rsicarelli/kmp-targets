package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure decision facts of the `single-target KSP` doctor finding (#73). The commonMain metadata
 * route (`kspCommonMainKotlinMetadata`) exists only when ≥2 platform targets share commonMain — the
 * same target-count rule as `compileCommonMainKotlinMetadata`
 * ([CommonMainMetadataCompilationInvariantTest]) and, as the ktorfit sample measured, KSP's own
 * route too; native presence is irrelevant. So a KSP plugin applied with exactly one registered
 * target means a processor that generates into commonMain is skipped. Unlike its sibling advisories
 * this drives only the doctor finding — no `warnOrFail`, no strict escalation — because the
 * "generates into commonMain?" conjunct is unobservable. These tests pin the two observable facts
 * the predicate keys off: KSP applied AND a single registered target.
 */
class SingleTargetKspFindingTest {

    private val android = KmpTarget.Jvm.Android
    private val jvm = KmpTarget.Jvm.Desktop
    private val iosArm64 = KmpTarget.Native.Apple.Ios.Arm64

    @Test
    fun `given KSP applied and a single target when the policy is computed then it flags`() {
        assertTrue(
            shouldWarnSingleTargetKsp(kspApplied = true, registered = KmpTargetSet.of(android))
        )
    }

    @Test
    fun `given KSP applied and a single native target when the policy is computed then it still flags`() {
        // Native presence is not the trigger — target count is. A lone native leaf has no shared
        // commonMain metadata route either.
        assertTrue(
            shouldWarnSingleTargetKsp(kspApplied = true, registered = KmpTargetSet.of(iosArm64))
        )
    }

    @Test
    fun `given KSP applied and two targets when the policy is computed then it does not flag`() {
        // ≥2 platform targets ⇒ the shared commonMain metadata route exists, so codegen runs (no
        // native required — proven by the jvm+js sample case).
        assertFalse(
            shouldWarnSingleTargetKsp(kspApplied = true, registered = KmpTargetSet.of(android, jvm))
        )
    }

    @Test
    fun `given KSP not applied and a single target when the policy is computed then it does not flag`() {
        assertFalse(
            shouldWarnSingleTargetKsp(kspApplied = false, registered = KmpTargetSet.of(android))
        )
    }

    @Test
    fun `given KSP applied and zero registrations when the policy is computed then it does not flag`() {
        // Zero targets is the inert-module state (#71), not this finding.
        assertFalse(shouldWarnSingleTargetKsp(kspApplied = true, registered = KmpTargetSet.empty))
    }
}
