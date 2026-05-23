package com.rsicarelli.kmptargets.hierarchy

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Each contributor's [HierarchyContributor.owns] predicate must match exactly its KGP family
 * membership across every known leaf. Guards against drift if a leaf is added to a branch.
 */
class HierarchyContributorTest {

    @Test
    fun `given NativeContributor when matched against all leaves then it owns exactly the native preset`() {
        assertOwns(NativeContributor, KmpTargetSet.native)
    }

    @Test
    fun `given AppleContributor when matched against all leaves then it owns exactly the apple preset`() {
        assertOwns(AppleContributor, KmpTargetSet.apple)
    }

    @Test
    fun `given IosContributor when matched against all leaves then it owns exactly the iOS leaves`() {
        assertOwns(IosContributor, KmpTargetSet.appleMobile)
    }

    @Test
    fun `given MacosContributor when matched against all leaves then it owns exactly the macOS leaves`() {
        assertOwns(MacosContributor, KmpTargetSet.appleDesktop)
    }

    @Test
    fun `given WatchosContributor when matched against all leaves then it owns exactly the watchOS leaves`() {
        assertOwns(WatchosContributor, KmpTargetSet.appleWatch)
    }

    @Test
    fun `given TvosContributor when matched against all leaves then it owns exactly the tvOS leaves`() {
        assertOwns(TvosContributor, KmpTargetSet.appleTv)
    }

    @Test
    fun `given LinuxContributor when matched against all leaves then it owns exactly the linux leaves`() {
        assertOwns(LinuxContributor, KmpTargetSet.linux)
    }

    @Test
    fun `given MingwContributor when matched against all leaves then it owns exactly the mingw leaf`() {
        assertOwns(MingwContributor, KmpTargetSet.mingw)
    }

    @Test
    fun `given AndroidNativeContributor when matched against all leaves then it owns exactly the androidNative leaves`() {
        assertOwns(AndroidNativeContributor, KmpTargetSet.androidNative)
    }

    @Test
    fun `given the contributor registry when listed then there is one per native group`() {
        assertEquals(
            HierarchyGroup.entries.toSet(),
            HierarchyContributor.all.map { it.group }.toSet(),
        )
        assertEquals(HierarchyContributor.all.size, HierarchyContributor.all.map { it.group }.size)
    }

    private fun assertOwns(contributor: HierarchyContributor, expected: KmpTargetSet) {
        assertEquals(expected.members, KmpTarget.all.filter { contributor.owns(it) }.toSet())
    }
}
