package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.model.RegisteredTarget
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class KmpTargetsExtensionTest {

    @Test
    fun `given extension created when defaultSelection accessed then property is unset`() {
        val ext = newExtension()
        assertFalse(ext.defaultSelection.isPresent)
    }

    @Test
    fun `given extension created when supports accessed then property is unset`() {
        val ext = newExtension()
        assertFalse(ext.supportsProperty.isPresent)
    }

    @Test
    fun `given supports unset when resolvedSupported then defaults to empty`() {
        val ext = newExtension()
        assertEquals(KmpTargetSet.empty, ext.resolvedSupported())
    }

    @Test
    fun `given defaultSelection set when read then it holds that value`() {
        val ext = newExtension()
        ext.defaultSelection.set(KmpTargetSet.web)
        assertTrue(ext.defaultSelection.isPresent)
        assertEquals(KmpTargetSet.web, ext.defaultSelection.get())
    }

    @Test
    fun `given nothing recorded when registered is read then it is empty`() {
        val ext = newExtension()
        assertEquals(KmpTargetSet.empty, ext.registered())
        assertEquals(KmpTargetSet.empty, ext.registered(KmpTargetSet.appleMobile))
    }

    @Test
    fun `given a recorded registration when registered is read then the snapshot holds the leaf`() {
        val ext = newExtension()
        ext.recordRegistration(KmpTarget.Native.Apple.Ios.Arm64, "iosArm64")
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), ext.registered())
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
            ext.registered(KmpTargetSet.appleMobile),
        )
        assertEquals(KmpTargetSet.empty, ext.registered(KmpTargetSet.web))
    }

    @Test
    fun `given an action that records another registration when fired then no concurrent modification occurs`() {
        // Re-entrancy guard: recordRegistration fires a snapshot of the actions, and onRegistered
        // replays a snapshot of the log — an action that re-enters (e.g. by calling supports())
        // must not blow up mid-iteration. Nested fire ordering stays unspecified.
        val ext = newExtension()
        val seen = mutableListOf<RegisteredTarget>()
        ext.onRegistered {
            if (it.leaf == KmpTarget.Jvm.Desktop) {
                ext.recordRegistration(KmpTarget.Native.Apple.Ios.Arm64, "iosArm64")
            }
            seen += it
        }
        ext.recordRegistration(KmpTarget.Jvm.Desktop, "jvm")
        assertEquals(
            setOf(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64),
            seen.map { it.leaf }.toSet(),
        )
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64),
            ext.registered(),
        )
    }

    @Test
    fun `given an action that hooks another action when replayed then no concurrent modification occurs`() {
        val ext = newExtension()
        ext.recordRegistration(KmpTarget.Jvm.Desktop, "jvm")
        val inner = mutableListOf<RegisteredTarget>()
        ext.onRegistered { ext.onRegistered { nested -> inner += nested } }
        assertEquals(listOf(KmpTarget.Jvm.Desktop), inner.map { it.leaf })
    }

    private fun newExtension(): KmpTargetsExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create("kmpTargets", KmpTargetsExtension::class.java)
    }
}
