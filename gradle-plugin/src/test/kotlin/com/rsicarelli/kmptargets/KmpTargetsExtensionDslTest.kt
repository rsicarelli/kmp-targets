package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

/**
 * Extension-level coverage of the type-safe `supported { … }` / `selection { … }` blocks, the raw
 * value overloads, and the [KmpTargetsExtension.resolvedSelection] precedence (global override wins
 * over a per-module selection). These assert the pure DSL → property wiring without applying KGP;
 * the in-process registration is covered by [KmpTargetsDslTest].
 */
class KmpTargetsExtensionDslTest {

    @Test
    fun `given supported block when effectiveSupported then equals the declared union`() {
        val ext = newExtension()
        ext.supported { mobile + web }
        assertEquals(KmpTargetSet.mobile + KmpTargetSet.web, ext.effectiveSupported())
    }

    @Test
    fun `given supported block with subtraction when effectiveSupported then the leaf is removed`() {
        val ext = newExtension()
        ext.supported { appleMobile - iosX64 }
        assertEquals(
            KmpTargetSet.appleMobile - KmpTarget.Native.Apple.Ios.X64,
            ext.effectiveSupported(),
        )
        assertTrue(KmpTarget.Native.Apple.Ios.X64 !in ext.effectiveSupported())
    }

    @Test
    fun `given supported value overload when effectiveSupported then equals that value`() {
        val ext = newExtension()
        ext.supported(KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Linux.X64))
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Linux.X64),
            ext.effectiveSupported(),
        )
    }

    @Test
    fun `given supported block called twice when effectiveSupported then the last assignment wins`() {
        val ext = newExtension()
        ext.supported { mobile }
        ext.supported { web }
        // The DSL assigns (replaces); it does not accumulate like the convention path.
        assertEquals(KmpTargetSet.web, ext.effectiveSupported())
    }

    @Test
    fun `given supported never declared when isSupportedDeclared then false and effectiveSupported is all`() {
        val ext = newExtension()
        assertFalse(ext.isSupportedDeclared())
        assertEquals(KmpTargetSet.all, ext.effectiveSupported())
    }

    @Test
    fun `given supported block when isSupportedDeclared then true`() {
        val ext = newExtension()
        ext.supported { jvm }
        assertTrue(ext.isSupportedDeclared())
    }

    @Test
    fun `given selection block and no global when resolvedSelection then equals the block value`() {
        val ext = newExtension()
        ext.selection { jvm + iosArm64 }
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64),
            ext.resolvedSelection(),
        )
    }

    @Test
    fun `given selection value overload and no global when resolvedSelection then equals that value`() {
        val ext = newExtension()
        ext.selection(KmpTargetSet.web)
        assertEquals(KmpTargetSet.web, ext.resolvedSelection())
    }

    @Test
    fun `given both a global selection and a selection block when resolvedSelection then global wins`() {
        val ext = newExtension()
        ext.selection { iosArm64 + iosSimulatorArm64 }
        ext.globalSelection = KmpTargetSet.of(KmpTarget.Jvm.Desktop)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Desktop), ext.resolvedSelection())
    }

    @Test
    fun `given a global selection narrowed to empty when resolvedSelection then it is empty not the block`() {
        val ext = newExtension()
        ext.selection { jvm }
        // An explicit "build nothing" global (e.g. KMP_TARGETS=jvm,-jvm) must win over the block.
        ext.globalSelection = KmpTargetSet.empty
        assertEquals(KmpTargetSet.empty, ext.resolvedSelection())
    }

    private fun newExtension(): KmpTargetsExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create("kmpTargets", KmpTargetsExtension::class.java)
    }
}
