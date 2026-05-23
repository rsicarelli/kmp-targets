package com.rsicarelli.kmptargets.hierarchy

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exhaustive proof of the collapse rule — the heart of the feature — as a pure function over the
 * active target set. Asserts the exact group set, nesting, and that redundant single-child groups
 * are collapsed. No Gradle involved.
 */
class HierarchyComposerTest {

    @Test
    fun `given a single iOS leaf when computed then no intermediate groups`() {
        val spec = computeHierarchySpec(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64))
        assertEquals(spec(leaf(KmpTarget.Native.Apple.Ios.Arm64)), spec)
        assertTrue(spec.allGroups().isEmpty())
    }

    @Test
    fun `given iOS only when computed then iosMain only and no appleMain or nativeMain`() {
        val spec = computeHierarchySpec(KmpTargetSet.appleMobile)
        assertEquals(spec(iosGroup()), spec)
        assertEquals(setOf(HierarchyGroup.IOS), spec.allGroups().toSet())
    }

    @Test
    fun `given iOS and macOS when computed then appleMain over iosMain and macosMain and no nativeMain`() {
        val spec = computeHierarchySpec(KmpTargetSet.appleMobile + KmpTargetSet.appleDesktop)
        assertEquals(spec(group(HierarchyGroup.APPLE, iosGroup(), macosGroup())), spec)
        assertTrue(HierarchyGroup.NATIVE !in spec.allGroups())
    }

    @Test
    fun `given iOS and a single linuxX64 when computed then nativeMain over iosMain and the linux leaf`() {
        val spec = computeHierarchySpec(KmpTargetSet.appleMobile + KmpTarget.Native.Linux.X64)
        assertEquals(
            spec(group(HierarchyGroup.NATIVE, iosGroup(), leaf(KmpTarget.Native.Linux.X64))),
            spec,
        )
        assertTrue(HierarchyGroup.APPLE !in spec.allGroups())
        assertTrue(HierarchyGroup.LINUX !in spec.allGroups())
    }

    @Test
    fun `given iOS and linux when computed then nativeMain over iosMain and linuxMain and no appleMain`() {
        val spec = computeHierarchySpec(KmpTargetSet.appleMobile + KmpTargetSet.linux)
        assertEquals(spec(group(HierarchyGroup.NATIVE, iosGroup(), linuxGroup())), spec)
        assertTrue(HierarchyGroup.APPLE !in spec.allGroups())
    }

    @Test
    fun `given all Apple when computed then appleMain over ios macos watchos tvos`() {
        val spec = computeHierarchySpec(KmpTargetSet.apple)
        assertEquals(
            spec(
                group(HierarchyGroup.APPLE, iosGroup(), macosGroup(), watchosGroup(), tvosGroup())
            ),
            spec,
        )
        assertTrue(HierarchyGroup.NATIVE !in spec.allGroups())
    }

    @Test
    fun `given native preset when computed then nativeMain over apple linux mingw-leaf androidNative`() {
        val spec = computeHierarchySpec(KmpTargetSet.native)
        val expected =
            spec(
                group(
                    HierarchyGroup.NATIVE,
                    group(
                        HierarchyGroup.APPLE,
                        iosGroup(),
                        macosGroup(),
                        watchosGroup(),
                        tvosGroup(),
                    ),
                    linuxGroup(),
                    leaf(KmpTarget.Native.Mingw.X64),
                    androidNativeGroup(),
                )
            )
        assertEquals(expected, spec)
    }

    @Test
    fun `given jvm only when computed then no groups`() {
        val spec = computeHierarchySpec(KmpTargetSet.of(KmpTarget.Jvm.Desktop))
        assertEquals(spec(leaf(KmpTarget.Jvm.Desktop)), spec)
        assertTrue(spec.allGroups().isEmpty())
    }

    @Test
    fun `given web only when computed then no groups`() {
        val spec = computeHierarchySpec(KmpTargetSet.web)
        assertTrue(spec.allGroups().isEmpty())
        assertEquals(
            setOf(KmpTarget.Web.Js, KmpTarget.Web.WasmJs, KmpTarget.Web.WasmWasi),
            spec.leafTargets(),
        )
    }

    @Test
    fun `given jvm and web when computed then no native groups`() {
        val spec = computeHierarchySpec(KmpTargetSet.of(KmpTarget.Jvm.Desktop) + KmpTargetSet.web)
        assertTrue(spec.allGroups().isEmpty())
    }

    @Test
    fun `given empty set when computed then empty spec`() {
        assertEquals(HierarchySpec(emptySet()), computeHierarchySpec(KmpTargetSet.empty))
    }

    @Test
    fun `given the full all set when computed then the complete canonical tree`() {
        val spec = computeHierarchySpec(KmpTargetSet.all)
        val expected =
            spec(
                group(
                    HierarchyGroup.NATIVE,
                    group(
                        HierarchyGroup.APPLE,
                        iosGroup(),
                        macosGroup(),
                        watchosGroup(),
                        tvosGroup(),
                    ),
                    linuxGroup(),
                    leaf(KmpTarget.Native.Mingw.X64),
                    androidNativeGroup(),
                ),
                leaf(KmpTarget.Jvm.Android),
                leaf(KmpTarget.Jvm.Desktop),
                leaf(KmpTarget.Web.Js),
                leaf(KmpTarget.Web.WasmJs),
                leaf(KmpTarget.Web.WasmWasi),
            )
        assertEquals(expected, spec)
    }

    @Test
    fun `given any single-leaf selection when computed then zero group nodes`() {
        for (target in KmpTarget.all) {
            val spec = computeHierarchySpec(KmpTargetSet.of(target))
            assertTrue(spec.allGroups().isEmpty(), "single leaf $target produced groups")
        }
    }

    @Test
    fun `given a single watchos leaf when computed then collapses through watchos apple and native`() {
        val spec = computeHierarchySpec(KmpTargetSet.of(KmpTarget.Native.Apple.Watchos.Arm64))
        assertEquals(spec(leaf(KmpTarget.Native.Apple.Watchos.Arm64)), spec)
    }

    // -- helpers --------------------------------------------------------------------------------

    private fun leaf(t: KmpTarget) = HierarchyNode.Leaf(t)

    private fun group(g: HierarchyGroup, vararg children: HierarchyNode) =
        HierarchyNode.Group(g, children.toSet())

    private fun spec(vararg roots: HierarchyNode) = HierarchySpec(roots.toSet())

    private fun iosGroup() =
        group(
            HierarchyGroup.IOS,
            leaf(KmpTarget.Native.Apple.Ios.Arm64),
            leaf(KmpTarget.Native.Apple.Ios.SimulatorArm64),
            leaf(KmpTarget.Native.Apple.Ios.X64),
        )

    private fun macosGroup() =
        group(
            HierarchyGroup.MACOS,
            leaf(KmpTarget.Native.Apple.Macos.Arm64),
            leaf(KmpTarget.Native.Apple.Macos.X64),
        )

    private fun watchosGroup() =
        group(
            HierarchyGroup.WATCHOS,
            leaf(KmpTarget.Native.Apple.Watchos.Arm64),
            leaf(KmpTarget.Native.Apple.Watchos.Arm32),
            leaf(KmpTarget.Native.Apple.Watchos.X64),
            leaf(KmpTarget.Native.Apple.Watchos.SimulatorArm64),
            leaf(KmpTarget.Native.Apple.Watchos.DeviceArm64),
        )

    private fun tvosGroup() =
        group(
            HierarchyGroup.TVOS,
            leaf(KmpTarget.Native.Apple.Tvos.Arm64),
            leaf(KmpTarget.Native.Apple.Tvos.X64),
            leaf(KmpTarget.Native.Apple.Tvos.SimulatorArm64),
        )

    private fun linuxGroup() =
        group(
            HierarchyGroup.LINUX,
            leaf(KmpTarget.Native.Linux.X64),
            leaf(KmpTarget.Native.Linux.Arm64),
        )

    private fun androidNativeGroup() =
        group(
            HierarchyGroup.ANDROID_NATIVE,
            leaf(KmpTarget.Native.AndroidNative.Arm32),
            leaf(KmpTarget.Native.AndroidNative.Arm64),
            leaf(KmpTarget.Native.AndroidNative.X86),
            leaf(KmpTarget.Native.AndroidNative.X64),
        )
}

private fun HierarchySpec.allGroups(): List<HierarchyGroup> = roots.flatMap { it.groupsRecursive() }

private fun HierarchyNode.groupsRecursive(): List<HierarchyGroup> =
    when (this) {
        is HierarchyNode.Leaf -> emptyList()
        is HierarchyNode.Group -> listOf(group) + children.flatMap { it.groupsRecursive() }
    }

private fun HierarchySpec.leafTargets(): Set<KmpTarget> =
    roots.flatMap { it.leavesRecursive() }.toSet()

private fun HierarchyNode.leavesRecursive(): List<KmpTarget> =
    when (this) {
        is HierarchyNode.Leaf -> listOf(target)
        is HierarchyNode.Group -> children.flatMap { it.leavesRecursive() }
    }
