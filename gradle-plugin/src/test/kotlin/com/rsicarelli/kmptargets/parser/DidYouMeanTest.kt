package com.rsicarelli.kmptargets.parser

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class DidYouMeanTest {

    private val sampleCandidates: Set<String> =
        setOf(
            "android",
            "androidTarget",
            "iosArm64",
            "iosSimulatorArm64",
            "jvm",
            "macosArm64",
            "appleMobile",
            "wasmJs",
        )

    @Test
    fun `given exact match in candidates when invoked then returns null`() {
        assertNull(didYouMean("iosArm64", sampleCandidates))
    }

    @Test
    fun `given single-character typo when invoked then returns the close candidate`() {
        assertEquals("iosArm64", didYouMean("iosArm46", sampleCandidates))
    }

    @Test
    fun `given character omission when invoked then returns the close candidate`() {
        assertEquals("android", didYouMean("androi", sampleCandidates))
    }

    @Test
    fun `given character insertion when invoked then returns the close candidate`() {
        assertEquals("jvm", didYouMean("jvmm", sampleCandidates))
    }

    @Test
    fun `given case-mismatched input when invoked then returns the canonical candidate`() {
        assertEquals("appleMobile", didYouMean("APPLEMOBILE", sampleCandidates))
    }

    @Test
    fun `given wildly different input when invoked then returns null`() {
        assertNull(didYouMean("xboxOne", sampleCandidates))
    }

    @Test
    fun `given input beyond default distance when invoked then returns null`() {
        // "andriot" is 3 edits away from "android" — beyond default maxDistance of 2
        assertNull(didYouMean("andriot", sampleCandidates))
    }

    @Test
    fun `given empty input when invoked then returns null`() {
        assertNull(didYouMean("", sampleCandidates))
    }

    @Test
    fun `given empty candidates when invoked then returns null`() {
        assertNull(didYouMean("anything", emptySet()))
    }

    @Test
    fun `given two candidates equally close when invoked then returns the shortest`() {
        // "iosArm64x" has distance 1 to "iosArm64" (length 8) — should suggest that one.
        val candidates = setOf("iosArm64", "iosSimulatorArm64")
        assertEquals("iosArm64", didYouMean("iosArm64x", candidates))
    }

    @Test
    fun `given custom maxDistance when invoked then respects the threshold`() {
        // "androids" is 1 edit from "android" — within both default and custom.
        assertEquals("android", didYouMean("androids", sampleCandidates, maxDistance = 1))
        // "andro" is 2 edits — within default but not maxDistance=1.
        assertEquals("android", didYouMean("andro", sampleCandidates, maxDistance = 2))
        assertNull(didYouMean("andro", sampleCandidates, maxDistance = 1))
    }

    @Test
    fun `given longer candidate matches when invoked then prefers closer match by edit distance`() {
        // "ioArm64" is closer to "iosArm64" (1 edit) than to "iosSimulatorArm64" (10+ edits).
        assertEquals("iosArm64", didYouMean("ioArm64", sampleCandidates))
    }
}
