package com.rsicarelli.kmptargets.hierarchy

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The no-collapse mode (issue #50): when collapse is disabled, a group materializes with **≥1**
 * present child instead of ≥2, so intermediate source sets like `iosMain` survive a single-leaf
 * selection — load-bearing for codebases with established `src/iosMain` dirs whose everyday
 * workflow narrows the selection to one iOS leaf. Empty groups are still dropped, and ungrouped
 * leaves (jvm/android/web) still never form groups.
 */
class HierarchyComposerNoCollapseTest {

    @Test
    fun `given a single iOS leaf and collapse disabled when computed then the full native apple ios chain materializes`() {
        val spec =
            computeHierarchySpec(
                KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
                collapse = false,
            )
        val expected =
            spec(
                group(
                    HierarchyGroup.NATIVE,
                    group(
                        HierarchyGroup.APPLE,
                        group(HierarchyGroup.IOS, leaf(KmpTarget.Native.Apple.Ios.Arm64)),
                    ),
                )
            )
        assertEquals(expected, spec)
    }

    @Test
    fun `given two iOS leaves and collapse disabled when computed then ios still groups and parents materialize`() {
        val spec =
            computeHierarchySpec(
                KmpTargetSet.of(
                    KmpTarget.Native.Apple.Ios.Arm64,
                    KmpTarget.Native.Apple.Ios.SimulatorArm64,
                ),
                collapse = false,
            )
        val expected =
            spec(
                group(
                    HierarchyGroup.NATIVE,
                    group(
                        HierarchyGroup.APPLE,
                        group(
                            HierarchyGroup.IOS,
                            leaf(KmpTarget.Native.Apple.Ios.Arm64),
                            leaf(KmpTarget.Native.Apple.Ios.SimulatorArm64),
                        ),
                    ),
                )
            )
        assertEquals(expected, spec)
    }

    @Test
    fun `given jvm only and collapse disabled when computed then no groups appear`() {
        // Ungrouped leaves attach directly to common in both modes — no-collapse must not invent
        // groups that never owned the leaf.
        val spec = computeHierarchySpec(KmpTargetSet.of(KmpTarget.Jvm.Desktop), collapse = false)
        assertEquals(spec(leaf(KmpTarget.Jvm.Desktop)), spec)
    }

    @Test
    fun `given an empty set and collapse disabled when computed then the spec is empty`() {
        // Groups with zero present children are dropped in both modes.
        assertEquals(
            HierarchySpec(emptySet()),
            computeHierarchySpec(KmpTargetSet.empty, collapse = false),
        )
    }

    @Test
    fun `given collapse enabled when computed then behavior is identical to the single-argument call`() {
        // Regression pin: the default mode must stay byte-identical to the pre-knob composer.
        for (set in
            listOf(
                KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
                KmpTargetSet.appleMobile,
                KmpTargetSet.apple,
                KmpTargetSet.native,
                KmpTargetSet.all,
                KmpTargetSet.web,
            )) {
            assertEquals(computeHierarchySpec(set), computeHierarchySpec(set, collapse = true))
        }
    }

    @Test
    fun `given ios and linux and collapse disabled when computed then apple materializes between native and ios`() {
        // With collapse on, this exact set produces nativeMain directly over iosMain + the linux
        // leaf (appleMain collapses away). No-collapse keeps every owning group on the path.
        val spec =
            computeHierarchySpec(
                KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64, KmpTarget.Native.Linux.X64),
                collapse = false,
            )
        val expected =
            spec(
                group(
                    HierarchyGroup.NATIVE,
                    group(
                        HierarchyGroup.APPLE,
                        group(HierarchyGroup.IOS, leaf(KmpTarget.Native.Apple.Ios.Arm64)),
                    ),
                    group(HierarchyGroup.LINUX, leaf(KmpTarget.Native.Linux.X64)),
                )
            )
        assertEquals(expected, spec)
        assertTrue(HierarchyGroup.APPLE in spec.roots.flatMap { it.groupsRecursiveLocal() })
    }

    // -- helpers ----------------------------------------------------------------------------

    private fun leaf(t: KmpTarget) = HierarchyNode.Leaf(t)

    private fun group(g: HierarchyGroup, vararg children: HierarchyNode) =
        HierarchyNode.Group(g, children.toSet())

    private fun spec(vararg roots: HierarchyNode) = HierarchySpec(roots.toSet())

    private fun HierarchyNode.groupsRecursiveLocal(): List<HierarchyGroup> =
        when (this) {
            is HierarchyNode.Leaf -> emptyList()
            is HierarchyNode.Group -> listOf(group) + children.flatMap { it.groupsRecursiveLocal() }
        }
}
