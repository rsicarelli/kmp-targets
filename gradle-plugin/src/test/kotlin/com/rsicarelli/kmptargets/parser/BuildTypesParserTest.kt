package com.rsicarelli.kmptargets.parser

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.junit.jupiter.api.Test

/**
 * Pure parsing facts of `kmptargets.framework.buildTypes` (#108): a comma-separated,
 * case-insensitive list over KGP's closed [NativeBuildType] enum, modelled on [parseKmpTargets] but
 * with no `+`/`-` algebra. Empty input defers (the declared build types win); an unknown token
 * fails with a did-you-mean. No Gradle here — the resolution/intersection wiring is proven by the
 * in-process suite.
 */
class BuildTypesParserTest {

    private fun ok(raw: String): Set<NativeBuildType> {
        val result = parseBuildTypes(raw)
        assertTrue(result is BuildTypesParseResult.Ok, "expected Ok for '$raw', was $result")
        return result.buildTypes
    }

    private fun err(raw: String): BuildTypesParseResult.Err {
        val result = parseBuildTypes(raw)
        assertTrue(result is BuildTypesParseResult.Err, "expected Err for '$raw', was $result")
        return result
    }

    @Test
    fun `given debug when parsed then it resolves to the DEBUG build type`() {
        assertEquals(setOf(NativeBuildType.DEBUG), ok("debug"))
    }

    @Test
    fun `given release when parsed then it resolves to the RELEASE build type`() {
        assertEquals(setOf(NativeBuildType.RELEASE), ok("release"))
    }

    @Test
    fun `given a comma list when parsed then it resolves to both build types`() {
        assertEquals(setOf(NativeBuildType.DEBUG, NativeBuildType.RELEASE), ok("debug,release"))
    }

    @Test
    fun `given mixed case and whitespace and reversed order when parsed then it is case-insensitive and order-insensitive`() {
        assertEquals(
            setOf(NativeBuildType.DEBUG, NativeBuildType.RELEASE),
            ok("  RELEASE , Debug "),
        )
    }

    @Test
    fun `given a repeated token when parsed then it is de-duplicated`() {
        assertEquals(setOf(NativeBuildType.DEBUG), ok("debug,DEBUG"))
    }

    @Test
    fun `given empty input when parsed then it is Ok with an empty set and a warning`() {
        val result = parseBuildTypes("")
        assertTrue(result is BuildTypesParseResult.Ok)
        assertTrue(result.buildTypes.isEmpty())
        assertTrue(result.warnings.isNotEmpty(), "empty input should warn, not fail")
    }

    @Test
    fun `given only separators when parsed then it is Ok with an empty set`() {
        assertTrue(ok(" , ").isEmpty())
    }

    @Test
    fun `given a near-miss token when parsed then it fails with a did-you-mean suggestion`() {
        val result = err("relese")
        assertEquals("relese", result.offendingToken)
        assertContains(result.message, "did you mean 'release'?")
    }

    @Test
    fun `given an unknown token among valid ones when parsed then it fails naming the offending token`() {
        val result = err("debug,foo")
        assertEquals("foo", result.offendingToken)
        assertContains(result.message, "foo")
        assertContains(result.message, "debug, release")
    }
}
