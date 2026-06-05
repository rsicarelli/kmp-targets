package com.rsicarelli.kmptargets.parser

import com.rsicarelli.kmptargets.model.KmpTargetSet

/**
 * Outcome of parsing a `kmptargets.targets` string into a [KmpTargetSet].
 *
 * [Ok] carries the resolved selection plus any non-fatal warnings (empty input, redundant tokens,
 * etc). [Err] carries a human-readable message and the offending token (when applicable) so the
 * plugin can fail the build with a precise diagnostic.
 */
public sealed interface ParseResult {

    public data class Ok(val set: KmpTargetSet, val warnings: List<String> = emptyList()) :
        ParseResult

    public data class Err(val message: String, val offendingToken: String?) : ParseResult
}
