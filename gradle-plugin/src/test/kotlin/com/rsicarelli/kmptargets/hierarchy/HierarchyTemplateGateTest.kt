package com.rsicarelli.kmptargets.hierarchy

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** The opt-out precedence: project DSL override > global property > built-in default (true). */
class HierarchyTemplateGateTest {

    @Test
    fun `given nothing set when resolved then enabled by default`() {
        assertTrue(resolveHierarchyTemplateEnabled(projectOverride = null, globalProperty = null))
    }

    @Test
    fun `given global property false and no project override when resolved then disabled`() {
        assertFalse(resolveHierarchyTemplateEnabled(projectOverride = null, globalProperty = false))
    }

    @Test
    fun `given global property true and no project override when resolved then enabled`() {
        assertTrue(resolveHierarchyTemplateEnabled(projectOverride = null, globalProperty = true))
    }

    @Test
    fun `given project override true and global false when resolved then project wins enabled`() {
        assertTrue(resolveHierarchyTemplateEnabled(projectOverride = true, globalProperty = false))
    }

    @Test
    fun `given project override false and global true when resolved then project wins disabled`() {
        assertFalse(resolveHierarchyTemplateEnabled(projectOverride = false, globalProperty = true))
    }

    @Test
    fun `given project override false and no global when resolved then disabled`() {
        assertEquals(
            false,
            resolveHierarchyTemplateEnabled(projectOverride = false, globalProperty = null),
        )
    }
}
