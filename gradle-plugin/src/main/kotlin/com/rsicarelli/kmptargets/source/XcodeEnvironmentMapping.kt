package com.rsicarelli.kmptargets.source

import com.rsicarelli.kmptargets.model.KmpTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

/**
 * Pure (no Gradle types) translation of Xcode's build-environment variables into kmp-targets'
 * vocabulary, for the opt-in Xcode-environment selection source (#109). When Xcode invokes Gradle
 * for `embedAndSign…AppleFrameworkForXcode` it exports `SDK_NAME` / `ARCHS` / `CONFIGURATION`; KGP
 * already reads them to drive the embed task, but its `XcodeEnvironment` / `AppleSdk` mapping is
 * `internal`, so the env-var contract is the only stable surface to map against. These functions
 * mirror that contract over Apple's own SDK/arch naming — a stable OS-level vocabulary, not KGP's
 * evolving API — and are unit-tested table-by-table.
 *
 * Both functions are total and **never throw**: an unrecognized SDK, an unrecognized or multi-value
 * `ARCHS`, or a custom build configuration with no fallback all resolve to `null`, so the selection
 * chain simply falls through to the next layer rather than failing the build.
 */

/**
 * Maps an `SDK_NAME` prefix plus a single `ARCHS` value to the registered KGP leaf, or `null` when
 * the pair is unknown/ambiguous (so the source defers to the next chain layer).
 *
 * `SDK_NAME` carries a version suffix (`iphonesimulator18.0`), so the platform is matched by
 * prefix. A multi-architecture `ARCHS` (a space-separated fat build, e.g. `"arm64 x86_64"`) is
 * deliberately **out of scope** (issue #109) and yields `null`; `IS_MACCATALYST` is likewise not
 * consulted.
 */
internal fun xcodeTargetLeaf(sdkName: String?, archs: String?): KmpTarget? {
    val sdk = sdkName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    val arch =
        archs
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotEmpty() }
            ?.singleOrNull() // multi-arch fat builds are out of scope → fall through
            ?.lowercase() ?: return null
    // Simulator prefixes are checked before their device counterparts; none is a prefix of another,
    // so the order is for readability, not correctness.
    return when {
        sdk.startsWith("iphonesimulator") ->
            when (arch) {
                "arm64" -> KmpTarget.Native.Apple.Ios.SimulatorArm64
                "x86_64" -> KmpTarget.Native.Apple.Ios.X64
                else -> null
            }
        sdk.startsWith("iphoneos") ->
            when (arch) {
                "arm64" -> KmpTarget.Native.Apple.Ios.Arm64
                else -> null
            }
        sdk.startsWith("appletvsimulator") ->
            when (arch) {
                "arm64" -> KmpTarget.Native.Apple.Tvos.SimulatorArm64
                "x86_64" -> KmpTarget.Native.Apple.Tvos.X64
                else -> null
            }
        sdk.startsWith("appletvos") ->
            when (arch) {
                "arm64" -> KmpTarget.Native.Apple.Tvos.Arm64
                else -> null
            }
        sdk.startsWith("watchsimulator") ->
            when (arch) {
                "arm64" -> KmpTarget.Native.Apple.Watchos.SimulatorArm64
                "x86_64" -> KmpTarget.Native.Apple.Watchos.X64
                else -> null
            }
        sdk.startsWith("watchos") ->
            when (arch) {
                "arm64" -> KmpTarget.Native.Apple.Watchos.DeviceArm64
                "arm64_32" -> KmpTarget.Native.Apple.Watchos.Arm64
                "armv7k" -> KmpTarget.Native.Apple.Watchos.Arm32
                else -> null
            }
        sdk.startsWith("macosx") ->
            when (arch) {
                "arm64" -> KmpTarget.Native.Apple.Macos.Arm64
                "x86_64" -> KmpTarget.Native.Apple.Macos.X64
                else -> null
            }
        else -> null
    }
}

/**
 * Maps Xcode's `CONFIGURATION` to a KGP [NativeBuildType], or `null` when neither it nor the
 * [kotlinFrameworkBuildType] fallback names a known build type.
 *
 * `Debug` / `Release` map directly (case-insensitive). A **custom** configuration (`Staging`, `QA`,
 * …) is not a build type, so — mirroring KGP's own behavior — the `KOTLIN_FRAMEWORK_BUILD_TYPE`
 * value (which a consumer sets in their Xcode build settings precisely to disambiguate custom
 * configurations) is consulted next; if that is also absent or unrecognized, the result is `null`.
 */
internal fun xcodeBuildType(
    configuration: String?,
    kotlinFrameworkBuildType: String?,
): NativeBuildType? {
    fun match(value: String?): NativeBuildType? =
        when (value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }) {
            "debug" -> NativeBuildType.DEBUG
            "release" -> NativeBuildType.RELEASE
            else -> null
        }
    return match(configuration) ?: match(kotlinFrameworkBuildType)
}
