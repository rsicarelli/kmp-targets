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
