package com.rsicarelli.kmptargets.hierarchy

/**
 * Resolves whether the minimal hierarchy template is enabled for a module.
 *
 * Precedence: per-project override ([projectOverride], the `kmpTargets { hierarchyTemplate }` DSL)
 * wins over the [globalProperty] default (`kmptargets.hierarchyTemplate`), which wins over the
 * built-in default of `true`. A `null` at a level means "not set — defer to the next level".
 */
internal fun resolveHierarchyTemplateEnabled(
    projectOverride: Boolean?,
    globalProperty: Boolean?,
): Boolean = projectOverride ?: globalProperty ?: true

/**
 * Resolves whether the single-child collapse rule is enabled (issue #50), with the exact same
 * precedence shape as the template gate: per-project override (the `kmpTargets { collapseHierarchy
 * }` DSL) wins over the global `kmptargets.hierarchyCollapse` property, which wins over the
 * built-in default of `true` (collapse — the minimal-tree behavior). Meaningless when the template
 * itself is disabled: KGP's default hierarchy owns the tree then.
 */
internal fun resolveHierarchyCollapseEnabled(
    projectOverride: Boolean?,
    globalProperty: Boolean?,
): Boolean = projectOverride ?: globalProperty ?: true
