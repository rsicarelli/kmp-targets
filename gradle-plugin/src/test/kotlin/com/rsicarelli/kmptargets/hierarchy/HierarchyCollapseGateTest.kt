package com.rsicarelli.kmptargets.hierarchy

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Precedence of the collapse knob (issue #50), mirroring the hierarchy-template gate exactly:
 * per-project DSL > global `kmptargets.hierarchyCollapse` > built-in default (`true`).
 */
class HierarchyCollapseGateTest {

    @Test
    fun `given nothing set when resolved then collapse defaults to true`() {
        assertTrue(resolveHierarchyCollapseEnabled(projectOverride = null, globalProperty = null))
    }

    @Test
    fun `given global false when resolved then collapse is false`() {
        assertFalse(resolveHierarchyCollapseEnabled(projectOverride = null, globalProperty = false))
    }

    @Test
    fun `given global true when resolved then collapse is true`() {
        assertTrue(resolveHierarchyCollapseEnabled(projectOverride = null, globalProperty = true))
    }

    @Test
    fun `given project false and no global when resolved then collapse is false`() {
        assertFalse(resolveHierarchyCollapseEnabled(projectOverride = false, globalProperty = null))
    }

    @Test
    fun `given project true over global false when resolved then the project override wins`() {
        assertTrue(resolveHierarchyCollapseEnabled(projectOverride = true, globalProperty = false))
    }

    @Test
    fun `given project false over global true when resolved then the project override wins`() {
        assertFalse(resolveHierarchyCollapseEnabled(projectOverride = false, globalProperty = true))
    }
}
