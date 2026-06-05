package com.rsicarelli.kmptargets.info

import com.rsicarelli.kmptargets.source.ConfigKeys

/**
 * Canonical human-readable labels for every layer of the global config precedence chain, printed by
 * `kmpTargetsInfo` as the selection's origin. The labels mirror the chain composed by
 * `labeledSources` in `KmpTargetsPlugin` (highest first): CLI `-P` → env → personal file →
 * committed file → `gradle.properties` → `local.properties`, then the two fallbacks when no layer
 * provides a non-blank value. The dedicated config files are labeled by their file names
 * ([ConfigKeys.LOCAL_FILE] / [ConfigKeys.COMMITTED_FILE]) directly at the composition site.
 *
 * Adding a future config layer means adding its label here and its entry to `labeledSources` —
 * nothing else.
 */
internal object OriginLabels {

    /** The eagerly read CLI start parameter — a genuine `-P<key>=…` on the command line. */
    fun cli(key: String): String = "command line (-P$key)"

    /** Gradle's native `ORG_GRADLE_PROJECT_<key>` env→project-property mapping. */
    fun environmentVariable(key: String): String =
        "environment variable ${ConfigKeys.ENV_PREFIX}$key"

    /**
     * The fused `providers.gradleProperty` layer. CLI and env are caught by the dedicated layers
     * above it, but this provider cannot tell the remaining origins apart — root
     * `gradle.properties`, `-Dorg.gradle.project.<key>`, and `~/.gradle/gradle.properties` all
     * resolve here — so the label names the whole group.
     */
    fun gradleProperties(key: String): String =
        "gradle.properties (also -Dorg.gradle.project.$key and ~/.gradle/gradle.properties)"

    const val LOCAL_PROPERTIES: String = "local.properties"

    /**
     * No config layer provided a value and `defaultSelection` holds `all`. The Property API cannot
     * distinguish an explicit `defaultSelection.set(KmpTargetSet.all)` from the untouched
     * convention; both report as built-in, which is value-identical and therefore still truthful.
     */
    const val DEFAULT_BUILTIN: String = "built-in default (all)"

    /** No config layer provided a value; build logic overrode `defaultSelection`. */
    const val DEFAULT_BUILD_LOGIC: String = "defaultSelection (build-logic)"
}
