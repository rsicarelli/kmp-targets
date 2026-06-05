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

    /** Every key the dedicated config files may legally contain (unknown keys fail the build). */
    val ALL: Set<String> = setOf(TARGETS, HIERARCHY_TEMPLATE)

    /** Committed, team-shared config file at the root of the build. */
    const val COMMITTED_FILE: String = "kmp-targets.properties"

    /** Git-ignored personal override file; its keys win over [COMMITTED_FILE]'s. */
    const val LOCAL_FILE: String = "kmp-targets.local.properties"

    /** Gradle's native env-var prefix for project properties (`ORG_GRADLE_PROJECT_<key>`). */
    const val ENV_PREFIX: String = "ORG_GRADLE_PROJECT_"
}
