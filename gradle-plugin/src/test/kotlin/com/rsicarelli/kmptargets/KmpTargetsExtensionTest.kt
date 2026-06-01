package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTargetSet
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

    private fun newExtension(): KmpTargetsExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create("kmpTargets", KmpTargetsExtension::class.java)
    }
}
