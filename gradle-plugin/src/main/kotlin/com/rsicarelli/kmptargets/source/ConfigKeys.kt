package com.rsicarelli.kmptargets.source

/**
 * Registry of every global configuration key the plugin reads, plus the dedicated config files that
 * may carry them. Single source of truth for the `kmptargets.` key namespace: adding a future
 * global knob means adding a key here (and to [ALL]), not inventing a new loose Gradle property.
 */
internal object ConfigKeys {

    /** Global selection — which targets to build now (comma grammar, see `parseKmpTargets`). */
    const val TARGETS: String = "kmptargets.targets"

    /** Global opt-out for the minimal hierarchy template (`true`/`false`). */
    const val HIERARCHY_TEMPLATE: String = "kmptargets.hierarchyTemplate"

    /**
     * Global opt-out for the single-child collapse rule (`true`/`false`, default `true` —
     * issue #50). When `false`, a group materializes with ≥1 present child, so intermediates like
     * `iosMain` survive a single-leaf selection. No effect when the template itself is disabled.
     */
    const val HIERARCHY_COLLAPSE: String = "kmptargets.hierarchyCollapse"

    /**
     * Global opt-in promoting the selection/host advisories to build failures (`true`/`false`,
     * default off — issue #34).
     */
    const val STRICT: String = "kmptargets.strict"

    /**
     * Global opt-in registering the `kmpCompileAll`/`kmpTestAll` umbrella lifecycle tasks
     * (`true`/`false`, default off — issue #77). Off by default because the umbrellas add
     * dependency edges to every registered target's compile/test tasks; a build only wants them
     * when it drives CI off one stable task name.
     */
    const val UMBRELLA_TASKS: String = "kmptargets.umbrellaTasks"

    /**
     * Global Apple-framework build types — which `NativeBuildType`s the framework links *now*
     * (`debug`/`release`, comma grammar, case-insensitive; see `parseBuildTypes`). Lane-shaped like
     * [TARGETS]: the effective build types are `property ∩ appleFramework(buildTypes = …)`, so the
     * property only ever narrows the module's declaration, never widens it (issue #108).
     */
    const val FRAMEWORK_BUILD_TYPES: String = "kmptargets.framework.buildTypes"

    /** Every key the dedicated config files may legally contain (unknown keys fail the build). */
    val ALL: Set<String> =
        setOf(
            TARGETS,
            HIERARCHY_TEMPLATE,
            HIERARCHY_COLLAPSE,
            STRICT,
            UMBRELLA_TASKS,
            FRAMEWORK_BUILD_TYPES,
        )

    /** Committed, team-shared config file at the root of the build. */
    const val COMMITTED_FILE: String = "kmp-targets.properties"

    /** Git-ignored personal override file; its keys win over [COMMITTED_FILE]'s. */
    const val LOCAL_FILE: String = "kmp-targets.local.properties"

    /** Gradle's native env-var prefix for project properties (`ORG_GRADLE_PROJECT_<key>`). */
    const val ENV_PREFIX: String = "ORG_GRADLE_PROJECT_"
}
