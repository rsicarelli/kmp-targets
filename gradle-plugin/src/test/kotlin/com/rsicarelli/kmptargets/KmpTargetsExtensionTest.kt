package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class KmpTargetsExtensionTest {

    @Test
    fun `given extension created when selection accessed then property is present and unset`() {
        val ext = newExtension()
        assertFalse(ext.selection.isPresent)
    }

    @Test
    fun `given extension created when fallback accessed then property is present and unset`() {
        val ext = newExtension()
        assertFalse(ext.fallback.isPresent)
    }

    @Test
    fun `given only when called then selection contains exactly those targets`() {
        val ext = newExtension()
        ext.only(KmpTarget.Jvm.Android, KmpTarget.Native.Apple.Ios.Arm64)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Native.Apple.Ios.Arm64),
            ext.selection.get(),
        )
    }

    @Test
    fun `given only called with no targets when invoked then selection is empty`() {
        val ext = newExtension()
        ext.only()
        assertEquals(KmpTargetSet.empty, ext.selection.get())
    }

    @Test
    fun `given convention set when exclude is called then selection is convention minus excluded`() {
        val ext = newExtension()
        ext.selection.convention(KmpTargetSet.appleMobile)
        ext.exclude(KmpTarget.Native.Apple.Ios.SimulatorArm64)
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), ext.selection.get())
    }

    @Test
    fun `given fallback set and no selection when exclude is called then exclude resolves against fallback`() {
        val ext = newExtension()
        ext.fallback.set(KmpTargetSet.appleMobile)
        ext.exclude(KmpTarget.Native.Apple.Ios.SimulatorArm64)
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), ext.selection.get())
    }

    @Test
    fun `given neither selection nor fallback when exclude is called then selection is empty`() {
        val ext = newExtension()
        ext.exclude(KmpTarget.Jvm.Android)
        assertEquals(KmpTargetSet.empty, ext.selection.get())
    }

    @Test
    fun `given only then exclude when invoked then exclude applies to the only-set value`() {
        val ext = newExtension()
        ext.only(KmpTarget.Jvm.Android, KmpTarget.Native.Apple.Ios.Arm64)
        ext.exclude(KmpTarget.Jvm.Android)
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), ext.selection.get())
    }

    @Test
    fun `given fallback set when selection is read then selection still empty until convention applied`() {
        val ext = newExtension()
        ext.fallback.set(KmpTargetSet.web)
        // Setting fallback doesn't auto-populate selection — that's the plugin's job
        assertFalse(ext.selection.isPresent)
        assertTrue(ext.fallback.isPresent)
        assertEquals(KmpTargetSet.web, ext.fallback.get())
    }

    private fun newExtension(): KmpTargetsExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create("kmpTargets", KmpTargetsExtension::class.java)
    }
}
