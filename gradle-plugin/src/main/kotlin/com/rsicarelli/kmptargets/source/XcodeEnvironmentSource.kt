package com.rsicarelli.kmptargets.source

import org.gradle.api.provider.Provider

/**
 * [SelectionSource] for the `kmptargets.targets` key, fed by Xcode's `SDK_NAME` + `ARCHS` (#109).
 * Emits the matched leaf's [id][com.rsicarelli.kmptargets.model.KmpTarget.id] — a token the
 * standard `parseKmpTargets` already accepts — or `null` (defer to the next layer) when the pair is
 * unknown/ambiguous.
 *
 * The env providers are captured at construction and read lazily, so each variable is tracked as
 * its own configuration-cache input. The source is only constructed when the opt-in flag is on, so
 * when the feature is off these variables are never read and add no config-cache inputs.
 */
internal class XcodeTargetsSource(
    private val sdkName: Provider<String>,
    private val archs: Provider<String>,
) : SelectionSource {

    override fun read(): String? = xcodeTargetLeaf(sdkName.orNull, archs.orNull)?.id
}

/**
 * [SelectionSource] for the `kmptargets.framework.buildTypes` key, fed by Xcode's `CONFIGURATION`
 * (with `KOTLIN_FRAMEWORK_BUILD_TYPE` as the custom-configuration fallback) (#109). Emits a
 * lower-cased `debug` / `release` token so it re-enters #108's existing `parseBuildTypes` path with
 * nothing duplicated, or `null` to defer to the next layer. Same lazy, per-variable config-cache
 * contract as [XcodeTargetsSource].
 */
internal class XcodeBuildTypesSource(
    private val configuration: Provider<String>,
    private val kotlinFrameworkBuildType: Provider<String>,
) : SelectionSource {

    override fun read(): String? =
        xcodeBuildType(configuration.orNull, kotlinFrameworkBuildType.orNull)?.name?.lowercase()
}
