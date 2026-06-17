package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure decision/message facts of the framework-unattached advisory (#107): a module that declared
 * an `appleFramework` and called `supports { }`, yet attached the framework to zero Apple leaves
 * (`registered ∩ on ∩ apple` is empty), ships a frameworkless artifact that breaks an Xcode build
 * downstream. The predicate keys off the actually-attached set, so it is cause-agnostic over WHY
 * the set is empty (a jvm-only selection, or an `on` scope narrower than what registered); the
 * `on`-scoped gate itself is proven end-to-end by [FrameworkUnattachedAdvisoryInProcessTest]. No
 * Gradle here; the wiring (warnOrFail + dedup + ordering + replay) is proven by the in-process
 * suite.
 */
class FrameworkUnattachedAdvisoryTest {

    private val iosArm64 = KmpTarget.Native.Apple.Ios.Arm64

    @Test
    fun `given a declared framework and supports with zero attached leaves when the policy is computed then it flags`() {
        assertTrue(
            shouldWarnFrameworkUnattached(
                frameworkDeclared = true,
                supportsDeclared = true,
                attachedLeaves = KmpTargetSet.empty,
            )
        )
    }

    @Test
    fun `given a declared framework with an attached apple leaf when the policy is computed then it does not flag`() {
        assertFalse(
            shouldWarnFrameworkUnattached(
                frameworkDeclared = true,
                supportsDeclared = true,
                attachedLeaves = KmpTargetSet.of(iosArm64),
            )
        )
    }

    @Test
    fun `given no framework declared when the policy is computed then it does not flag`() {
        // The advisory is scoped to declared frameworks: a module that never called appleFramework
        // has nothing to attach, so an empty attached set is not a problem.
        assertFalse(
            shouldWarnFrameworkUnattached(
                frameworkDeclared = false,
                supportsDeclared = true,
                attachedLeaves = KmpTargetSet.empty,
            )
        )
    }

    @Test
    fun `given supports never declared when the policy is computed then it does not flag`() {
        // Explicit-selection doctrine: a module that never called supports { } registered nothing
        // intentionally — the doctor still renders the declared-but-unattached facts, but there is
        // no advisory.
        assertFalse(
            shouldWarnFrameworkUnattached(
                frameworkDeclared = true,
                supportsDeclared = false,
                attachedLeaves = KmpTargetSet.empty,
            )
        )
    }

    @Test
    fun `given a module path and baseName when the warning is built then it names the module the framework the gate the consequence and the fix`() {
        val message = frameworkUnattachedWarning(":shared", "KotlinShared")
        assertContains(message, ":shared")
        assertContains(message, "KotlinShared")
        assertContains(message, "no Apple target in its scope")
        assertContains(message, "Xcode")
        assertContains(message, "Widen the selection")
        assertContains(message, "registered(apple).isEmpty()")
    }
}
