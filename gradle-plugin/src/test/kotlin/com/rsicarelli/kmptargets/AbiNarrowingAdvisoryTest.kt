package com.rsicarelli.kmptargets

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Pure-message tests for the ABI × selection narrowing advisory (#81). */
class AbiNarrowingAdvisoryTest {

    @Test
    fun `given uncovered targets when the warning renders then it names the path the task and the targets`() {
        val message = abiNarrowingWarning(":lib", "apiCheck", listOf("js", "linuxX64"))
        assertTrue(":lib" in message, message)
        assertTrue("apiCheck" in message, message)
        assertTrue("js, linuxX64" in message, message)
    }

    @Test
    fun `given the advisory when it renders then it points at the full-selection CI lane and strict mode`() {
        val message = abiNarrowingWarning(":lib", "apiDump", listOf("linuxX64"))
        assertTrue("full selection" in message, message)
        assertTrue("CI" in message, message)
        assertTrue("strict" in message, message)
        // It must never claim to be the safety net itself — it points at the lane that is.
        assertFalse("guarantees" in message, message)
    }
}
