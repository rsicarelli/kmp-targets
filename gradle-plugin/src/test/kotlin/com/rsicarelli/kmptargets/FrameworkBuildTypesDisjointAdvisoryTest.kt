package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.junit.jupiter.api.Test

/**
 * Pure decision/message facts of the framework build-types-disjoint advisory (#108): a module that
 * declared an `appleFramework`, attached it to ≥1 Apple leaf, yet whose lane
 * `kmptargets.framework.buildTypes` shares no [NativeBuildType] with the declaration links nothing.
 * The build-type analog of empty-overlap on the `selection ∩ supported` axis, and mutually
 * exclusive with the unattached advisory (#107) — that one needs zero attached leaves, this one
 * needs ≥1. No Gradle here; the wiring (warnOrFail + dedup + replay + mutual exclusivity) is proven
 * by the in-process suite.
 */
class FrameworkBuildTypesDisjointAdvisoryTest {

    private val attached = KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64)
    private val debug = setOf(NativeBuildType.DEBUG)
    private val release = setOf(NativeBuildType.RELEASE)

    @Test
    fun `given a declared framework attached with a disjoint property when the policy is computed then it flags`() {
        assertTrue(
            shouldWarnFrameworkBuildTypesDisjoint(
                frameworkDeclared = true,
                property = release,
                declared = debug,
                attachedLeaves = attached,
            )
        )
    }

    @Test
    fun `given a property that overlaps the declaration when the policy is computed then it does not flag`() {
        assertFalse(
            shouldWarnFrameworkBuildTypesDisjoint(
                frameworkDeclared = true,
                property = setOf(NativeBuildType.DEBUG, NativeBuildType.RELEASE),
                declared = debug,
                attachedLeaves = attached,
            )
        )
    }

    @Test
    fun `given no property set when the policy is computed then it does not flag`() {
        // Absent property → the declaration wins verbatim; there is nothing to be disjoint from.
        assertFalse(
            shouldWarnFrameworkBuildTypesDisjoint(
                frameworkDeclared = true,
                property = null,
                declared = debug,
                attachedLeaves = attached,
            )
        )
    }

    @Test
    fun `given zero attached leaves when the policy is computed then it does not flag`() {
        // Mutually exclusive with the unattached advisory (#107): with nothing attached there is no
        // framework to link, so the unattached advisory owns that state, not this one.
        assertFalse(
            shouldWarnFrameworkBuildTypesDisjoint(
                frameworkDeclared = true,
                property = release,
                declared = debug,
                attachedLeaves = KmpTargetSet.empty,
            )
        )
    }

    @Test
    fun `given no framework declared when the policy is computed then it does not flag`() {
        assertFalse(
            shouldWarnFrameworkBuildTypesDisjoint(
                frameworkDeclared = false,
                property = release,
                declared = debug,
                attachedLeaves = attached,
            )
        )
    }

    @Test
    fun `given a module path baseName property and declaration when the warning is built then it names them the consequence and the fix`() {
        val message = frameworkBuildTypesDisjointWarning(":shared", "KotlinShared", release, debug)
        assertContains(message, ":shared")
        assertContains(message, "KotlinShared")
        assertContains(message, "kmptargets.framework.buildTypes")
        assertContains(message, "RELEASE")
        assertContains(message, "DEBUG")
        assertContains(message, "Xcode")
    }
}
