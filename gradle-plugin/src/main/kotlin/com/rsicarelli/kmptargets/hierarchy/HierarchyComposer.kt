package com.rsicarelli.kmptargets.hierarchy

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet

/**
 * Computes the minimal correct hierarchy for [active] — the heart of the feature.
 *
 * Walks the fixed group taxonomy ([HierarchyGroup]) restricted to the active leaves and applies the
 * collapse rule uniformly, bottom-up: a group materializes a source set only when it merges ≥2
 * present children; a group with exactly one present child collapses (that child reconnects to the
 * group's parent, so collapses chain); a group with no present children is dropped. A single leaf
 * is always present.
 *
 * Consequence (the article's point): an iOS-only module yields `iosMain` only — no redundant
 * `appleMain`/`nativeMain` — while iOS + linux yields `nativeMain` over `iosMain` + `linuxMain`.
 */
internal fun computeHierarchySpec(active: KmpTargetSet): HierarchySpec {
    // The native subtree, collapsed; plus any leaf that belongs to no group (jvm/android/web),
    // which always attaches directly to `common`.
    val nativeRoots = contributionOf(HierarchyGroup.NATIVE, active)
    val ungrouped =
        active.members
            .filter { mostSpecificGroup(it) == null }
            .map<KmpTarget, HierarchyNode> { HierarchyNode.Leaf(it) }
            .toSet()
    return HierarchySpec(nativeRoots + ungrouped)
}

/**
 * The set of nodes [group] contributes to its parent's children, with the collapse rule applied: ≥2
 * present children ⇒ a single [HierarchyNode.Group]; exactly one ⇒ that child bubbles up unchanged
 * (collapse); none ⇒ nothing.
 */
private fun contributionOf(group: HierarchyGroup, active: KmpTargetSet): Set<HierarchyNode> {
    val fromChildGroups: Set<HierarchyNode> =
        HierarchyGroup.entries
            .filter { it.parent == group }
            .flatMap { contributionOf(it, active) }
            .toSet()
    val directLeaves: Set<HierarchyNode> =
        active.members
            .filter { mostSpecificGroup(it) == group }
            .map { HierarchyNode.Leaf(it) }
            .toSet()
    val presentChildren = fromChildGroups + directLeaves
    return when (presentChildren.size) {
        0 -> emptySet()
        1 -> presentChildren
        else -> setOf(HierarchyNode.Group(group, presentChildren))
    }
}

/** The deepest group that owns [target], or `null` if no contributor owns it (jvm/android/web). */
private fun mostSpecificGroup(target: KmpTarget): HierarchyGroup? =
    HierarchyContributor.all.filter { it.owns(target) }.maxByOrNull { it.group.depth }?.group
