package com.rsicarelli.kmptargets.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

/**
 * Pins the deprecated-target set to the official docs page
 * (https://kotlinlang.org/docs/native-target-support.html, as of Kotlin 2.3.20): exactly the three
 * x86-64 Apple leaves. A Kotlin upgrade that changes the page must come through this test — the set
 * is hand-stamped, so drift is a deliberate review, never an accident.
 */
class KmpTargetDeprecationTest {

    @Test
    fun `given the model when deprecated leaves are collected then they are exactly the three x64 apple leaves`() {
        assertEquals(
            setOf("macosX64", "tvosX64", "watchosX64"),
            KmpTarget.deprecated.map { it.id }.toSet(),
        )
    }

    @Test
    fun `given the deprecated registry when compared to the catalog then it is a strict subset of all`() {
        // The registry may only ever name leaves the plugin actually ships.
        assertEquals(KmpTarget.deprecated, KmpTarget.deprecated intersect KmpTarget.all)
    }

    @Test
    fun `given iosX64 when checked then it is tier-3 but not deprecated`() {
        // The docs page keeps iosX64 (the Intel-mac simulator) in tier 3 — supported, not
        // deprecated. It must never be swept up with the deprecated x64 trio.
        assertFalse(KmpTarget.Native.Apple.Ios.X64 in KmpTarget.deprecated)
    }

    @Test
    fun `given the deprecated leaves when presets are read then they still contain them`() {
        // Presets stay faithful to KGP's target set: deprecation adds signal, never filtering.
        assertEquals(true, KmpTarget.Native.Apple.Macos.X64 in KmpTargetSet.appleDesktop)
        assertEquals(true, KmpTarget.Native.Apple.Watchos.X64 in KmpTargetSet.appleWatch)
        assertEquals(true, KmpTarget.Native.Apple.Tvos.X64 in KmpTargetSet.appleTv)
        assertEquals(true, KmpTarget.Native.Apple.Macos.X64 in KmpTargetSet.all)
    }
}
