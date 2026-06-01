package com.rsicarelli.kmptargets

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
        assertFalse(ext.selectionProperty.isPresent)
    }

    @Test
    fun `given extension created when fallback accessed then property is present and unset`() {
        val ext = newExtension()
        assertFalse(ext.fallback.isPresent)
    }

    @Test
    fun `given extension created when supported accessed then property is unset`() {
        val ext = newExtension()
        assertFalse(ext.supportedProperty.isPresent)
    }

    @Test
    fun `given supported unset when effectiveSupported then defaults to all`() {
        val ext = newExtension()
        assertEquals(KmpTargetSet.all, ext.effectiveSupported())
    }

    @Test
    fun `given fallback set when selection is read then selection still empty until plugin applied`() {
        val ext = newExtension()
        ext.fallback.set(KmpTargetSet.web)
        // Setting fallback doesn't auto-populate selection — that's the plugin's job.
        assertFalse(ext.selectionProperty.isPresent)
        assertTrue(ext.fallback.isPresent)
        assertEquals(KmpTargetSet.web, ext.fallback.get())
    }

    private fun newExtension(): KmpTargetsExtension {
        val project = ProjectBuilder.builder().build()
        return project.extensions.create("kmpTargets", KmpTargetsExtension::class.java)
    }
}
