package com.rsicarelli.kmptargets.parser

import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

/**
 * Outcome of parsing a `kmptargets.framework.buildTypes` value into a set of KGP [NativeBuildType]
 * (issue #108). Mirrors [ParseResult] in shape — [Ok] carries the resolved set plus any non-fatal
 * warnings, [Err] a human-readable message and the offending token — but resolves to KGP's **own**
 * closed `NativeBuildType` enum rather than a [com.rsicarelli.kmptargets.model.KmpTargetSet], so
 * the plugin routes build types straight to the real KGP vocabulary with nothing mirrored.
 */
internal sealed interface BuildTypesParseResult {

    data class Ok(val buildTypes: Set<NativeBuildType>, val warnings: List<String> = emptyList()) :
        BuildTypesParseResult

    data class Err(val message: String, val offendingToken: String?) : BuildTypesParseResult
}

/**
 * Parses a `kmptargets.framework.buildTypes` value into a set of [NativeBuildType].
 *
 * Grammar (informal): a comma-separated list of `debug` / `release`, each token trimmed and matched
 * **case-insensitively** against KGP's closed [NativeBuildType] enum; the result is an
 * order-insensitive, de-duplicated set. There is deliberately **no** `+`/`-` algebra (unlike
 * `kmptargets.targets`): the vocabulary is two closed values KGP will never extend, so the
 * additive/ subtractive grammar would be ceremony.
 *
 * - Empty input (or input containing only separators) returns [BuildTypesParseResult.Ok] with an
 *   empty set and a warning; the plugin then treats it as "not set" and keeps the DSL-declared
 *   build types — mirroring how an empty `kmptargets.targets` falls back to the default selection.
 * - An unknown token fails strictly, with a did-you-mean suggestion when a known token is within
 *   edit distance 2 (`relese` → `release`).
 */
internal fun parseBuildTypes(raw: String): BuildTypesParseResult {
    val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) {
        return BuildTypesParseResult.Ok(
            emptySet(),
            warnings =
                listOf("kmptargets.framework.buildTypes is empty; using the declared build types"),
        )
    }

    val byName: Map<String, NativeBuildType> =
        NativeBuildType.entries.associateBy { it.name.lowercase() }
    val knownNames: Set<String> = byName.keys

    val resolved = linkedSetOf<NativeBuildType>()
    for (token in tokens) {
        val buildType =
            byName[token.lowercase()]
                ?: return BuildTypesParseResult.Err(
                    message = unknownBuildTypeMessage(token, token.lowercase(), knownNames),
                    offendingToken = token,
                )
        resolved += buildType
    }
    return BuildTypesParseResult.Ok(resolved)
}

private fun unknownBuildTypeMessage(
    original: String,
    lowered: String,
    knownNames: Set<String>,
): String {
    val known = knownNames.sorted().joinToString(", ")
    val suggestion = didYouMean(lowered, knownNames)
    return if (suggestion != null) {
        "unknown build type '$original' — did you mean '$suggestion'? (known: $known)"
    } else {
        "unknown build type '$original' (known: $known)"
    }
}
