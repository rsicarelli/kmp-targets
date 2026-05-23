package com.rsicarelli.kmptargets.parser

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet

/**
 * Parses a `KMP_TARGETS` value into a [KmpTargetSet].
 *
 * Grammar (informal):
 * ```
 * expr   := atom (',' atom)*
 * atom   := ('+' | '-')? token
 * token  := <leaf-id> | <leaf-alias> | <preset-name>
 * preset := "all" | "appleMobile" | "appleDesktop" | "apple" | "web" | "jvmFamily"
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
            warnings = listOf("KMP_TARGETS is empty; using fallback selection"),
        )
    }

    val byCanonical: Map<String, KmpTarget> = registry.associateBy { it.id.lowercase() }
    val byAlias: Map<String, KmpTarget> = buildMap {
        registry.forEach { target ->
            target.aliases.forEach { alias -> put(alias.lowercase(), target) }
        }
    }
    val presets: Map<String, KmpTargetSet> = PRESETS

    val knownNames: Set<String> = byCanonical.keys + byAlias.keys + presets.keys

    var accumulator = KmpTargetSet.empty
    for (rawToken in tokens) {
        val (sign, name) = splitSign(rawToken)
        if (name.isEmpty()) {
            return ParseResult.Err(
                message = "KMP_TARGETS token '$rawToken' has a sign but no target name",
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

private val PRESETS: Map<String, KmpTargetSet> =
    mapOf(
        "all" to KmpTargetSet.all,
        "applemobile" to KmpTargetSet.appleMobile,
        "appledesktop" to KmpTargetSet.appleDesktop,
        "apple" to KmpTargetSet.apple,
        "web" to KmpTargetSet.web,
        "jvmfamily" to KmpTargetSet.jvmFamily,
    )

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
        "watchos" to "no watchOS leaves are shipped yet — track the roadmap for support",
        "tvos" to "no tvOS leaves are shipped yet — track the roadmap for support",
        "linux" to "no Linux leaves are shipped yet — track the roadmap for support",
        "mingw" to "no MinGW leaves are shipped yet — track the roadmap for support",
        "androidnative" to
            "no Android Native leaves are shipped yet — track the roadmap for support",
        "native" to
            "'native' covers many platforms — use a specific leaf or a family preset like 'apple'",
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
        return "KMP_TARGETS: '$original' is a target family, not a selectable target — $hint"
    }
    val suggestion = didYouMean(lowered, knownNames)
    return if (suggestion != null) {
        "KMP_TARGETS: unknown target '$original' — did you mean '$suggestion'?"
    } else {
        "KMP_TARGETS: unknown target '$original'"
    }
}
