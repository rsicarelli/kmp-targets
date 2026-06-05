package com.rsicarelli.kmptargets.parser

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet

/**
 * Parses a `kmptargets.targets` value into a [KmpTargetSet].
 *
 * Grammar (informal):
 * ```
 * expr   := atom (',' atom)*
 * atom   := ('+' | '-')? token
 * token  := <leaf-id> | <leaf-alias> | <preset-name>
 * preset := "all" | "native" | "apple" | "appleMobile" | "appleDesktop" | "appleWatch"
 *         | "appleTv" | "linux" | "mingw" | "windows" | "androidNative" | "web" | "jvmFamily"
 *         | "mobile"
 * ```
 *
 * Resolution is a left-to-right fold. The first token may omit a sign (defaults to additive).
 * Tokens are trimmed and matched case-insensitively against canonical ids, aliases, and preset
 * names.
 *
 * - Unknown tokens fail strictly, with a did-you-mean suggestion if a known token is within edit
 *   distance 2.
 * - Bare family names ([FAMILY_HINTS]) fail with a hint pointing at the relevant preset or leaf.
 * - Empty input (or input containing only separators) returns [ParseResult.Ok] with
 *   [KmpTargetSet.empty] and a warning; the plugin then falls back to its configured default
 *   selection.
 */
public fun parseKmpTargets(raw: String, registry: Set<KmpTarget> = KmpTarget.all): ParseResult {
    val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) {
        return ParseResult.Ok(
            KmpTargetSet.empty,
            warnings = listOf("kmptargets.targets is empty; using the default selection"),
        )
    }

    val byCanonical: Map<String, KmpTarget> = registry.associateBy { it.id.lowercase() }
    val byAlias: Map<String, KmpTarget> = buildMap {
        registry.forEach { target ->
            target.aliases.forEach { alias -> put(alias.lowercase(), target) }
        }
    }
    val presets: Map<String, KmpTargetSet> = PRESETS

    val knownNames: Set<String> =
        registry.flatMap { target -> listOf(target.id) + target.aliases }.toSet() + PRESET_NAMES

    var accumulator = KmpTargetSet.empty
    for (rawToken in tokens) {
        val (sign, name) = splitSign(rawToken)
        if (name.isEmpty()) {
            return ParseResult.Err(
                message = "token '$rawToken' has a sign but no target name",
                offendingToken = rawToken,
            )
        }
        val lower = name.lowercase()
        val resolved: KmpTargetSet =
            byCanonical[lower]?.let { KmpTargetSet.of(it) }
                ?: byAlias[lower]?.let { KmpTargetSet.of(it) }
                ?: presets[lower]
                ?: return ParseResult.Err(
                    message = unknownTokenMessage(name, lower, knownNames),
                    offendingToken = name,
                )
        accumulator =
            when (sign) {
                '+' -> accumulator + resolved
                '-' -> accumulator - resolved
                else -> error("unreachable sign: $sign")
            }
    }
    return ParseResult.Ok(accumulator)
}

private val PRESETS_BY_NAME: Map<String, KmpTargetSet> =
    mapOf(
        "all" to KmpTargetSet.all,
        "native" to KmpTargetSet.native,
        "appleMobile" to KmpTargetSet.appleMobile,
        "appleDesktop" to KmpTargetSet.appleDesktop,
        "appleWatch" to KmpTargetSet.appleWatch,
        "appleTv" to KmpTargetSet.appleTv,
        "apple" to KmpTargetSet.apple,
        "linux" to KmpTargetSet.linux,
        "mingw" to KmpTargetSet.mingw,
        "windows" to KmpTargetSet.mingw,
        "androidNative" to KmpTargetSet.androidNative,
        "web" to KmpTargetSet.web,
        "jvmFamily" to KmpTargetSet.jvmFamily,
        "mobile" to KmpTargetSet.mobile,
    )

private val PRESETS: Map<String, KmpTargetSet> = PRESETS_BY_NAME.mapKeys { it.key.lowercase() }

private val PRESET_NAMES: Set<String> = PRESETS_BY_NAME.keys

/**
 * Lowercase family names users commonly try to use as selectors. None of them resolve to a leaf,
 * alias, or preset — but they're close enough to user intent to deserve a tailored error rather
 * than a generic did-you-mean.
 */
private val FAMILY_HINTS: Map<String, String> =
    mapOf(
        "ios" to
            "use a specific leaf like 'iosArm64' or 'iosSimulatorArm64', or the 'appleMobile' preset",
        "macos" to
            "use a specific leaf like 'macosArm64' or 'macosX64', or the 'appleDesktop' preset",
        "watchos" to
            "use a specific leaf like 'watchosArm64' or 'watchosSimulatorArm64', or the 'appleWatch' preset",
        "tvos" to
            "use a specific leaf like 'tvosArm64' or 'tvosSimulatorArm64', or the 'appleTv' preset",
    )

private fun splitSign(token: String): Pair<Char, String> =
    when {
        token.startsWith('+') -> '+' to token.substring(1).trim()
        token.startsWith('-') -> '-' to token.substring(1).trim()
        else -> '+' to token
    }

private fun unknownTokenMessage(
    original: String,
    lowered: String,
    knownNames: Set<String>,
): String {
    FAMILY_HINTS[lowered]?.let { hint ->
        return "'$original' is a target family, not a selectable target — $hint"
    }
    val suggestion = didYouMean(lowered, knownNames)
    return if (suggestion != null) {
        "unknown target '$original' — did you mean '$suggestion'?"
    } else {
        "unknown target '$original'"
    }
}
