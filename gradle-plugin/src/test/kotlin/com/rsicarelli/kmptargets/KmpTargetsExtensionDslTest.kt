package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Extension-level coverage of the type-safe `supports { … }` block, its raw value overload, and the
 * [KmpTargetsExtension.resolvedSelection] precedence (a global `KMP_TARGETS` wins over
 * [KmpTargetsExtension.defaultSelection]). These assert the pure DSL → property wiring without
 * applying KGP; the in-process registration is covered by [KmpTargetsDslTest].
 */
class KmpTargetsExtensionDslTest {

    @Test
    fun `given supports block when resolvedSupported then equals the declared union`() {
        val ext = newExtension()
        ext.supports { mobile + web }
        assertEquals(KmpTargetSet.mobile + KmpTargetSet.web, ext.resolvedSupported())
    }

    @Test
    fun `given supports block with subtraction when resolvedSupported then the leaf is removed`() {
        val ext = newExtension()
        ext.supports { appleMobile - iosX64 }
        assertEquals(
            KmpTargetSet.appleMobile - KmpTarget.Native.Apple.Ios.X64,
            ext.resolvedSupported(),
        )
        assertTrue(KmpTarget.Native.Apple.Ios.X64 !in ext.resolvedSupported())
    }

    @Test
    fun `given supports value overload when resolvedSupported then equals that value`() {
        val ext = newExtension()
        ext.supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Linux.X64))
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Linux.X64),
            ext.resolvedSupported(),
        )
    }

    @Test
    fun `given supports block called twice when resolvedSupported then the sets union`() {
        val ext = newExtension()
        ext.supports { mobile }
        ext.supports { web }
        // Registration is one-way (an already-registered target can't be retracted), so repeated
        // calls union rather than replace. To narrow, write the final set in a single block.
        assertEquals(KmpTargetSet.mobile + KmpTargetSet.web, ext.resolvedSupported())
    }

    @Test
    fun `given supports never declared when resolvedSupported then defaults to empty`() {
        val ext = newExtension()
        assertEquals(KmpTargetSet.empty, ext.resolvedSupported())
    }

    @Test
    fun `given supports block declared when resolvedSupported then equals the block value`() {
        val ext = newExtension()
        ext.supports { jvm }
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Desktop), ext.resolvedSupported())
    }

    @Test
    fun `given defaultSelection set and no global when resolvedSelection then equals that value`() {
        val ext = newExtension()
        ext.defaultSelection.set(
            KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64)
        )
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64),
            ext.resolvedSelection(),
        )
    }

    @Test
    fun `given a global selection and a defaultSelection when resolvedSelection then global wins`() {
        val ext = newExtension()
        ext.defaultSelection.set(KmpTargetSet.appleMobile)
        ext.globalSelection = KmpTargetSet.of(KmpTarget.Jvm.Desktop)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Desktop), ext.resolvedSelection())
    }

    @Test
    fun `given a global selection narrowed to empty when resolvedSelection then it is empty not the default`() {
        val ext = newExtension()
        ext.defaultSelection.set(KmpTargetSet.of(KmpTarget.Jvm.Desktop))
        // An explicit "build nothing" global (e.g. KMP_TARGETS=jvm,-jvm) must win over the default.
        ext.globalSelection = KmpTargetSet.empty
        assertEquals(KmpTargetSet.empty, ext.resolvedSelection())
    }

    private fun newExtension(): KmpTargetsExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create("kmpTargets", KmpTargetsExtension::class.java)
    }
}
