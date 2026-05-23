package com.rsicarelli.kmptargets.hierarchy

import com.rsicarelli.kmptargets.model.KmpTarget

/**
 * The minimal hierarchy to apply, as a pure tree rooted at `common`.
 *
 * [roots] are the nodes that attach directly under `commonMain`. Children are held as [Set]s so
 * equality is order-independent — the collapse rule and its tests compare specs structurally, and
 * KGP does not care about the order `with…()`/`group()` calls are emitted in.
 */
internal data class HierarchySpec(val roots: Set<HierarchyNode>)

internal sealed interface HierarchyNode {

    /** An intermediate group source set (e.g. `appleMain`), invariant: always ≥2 [children]. */
    data class Group(val group: HierarchyGroup, val children: Set<HierarchyNode>) : HierarchyNode

    /** A single KMP target leaf (e.g. `iosArm64Main`). */
    data class Leaf(val target: KmpTarget) : HierarchyNode
}
