package com.rsicarelli.kmptargets.abi

import java.io.File

/**
 * Layout-agnostic detection of committed ABI dumps and which targets they cover, for the
 * signal-only coverage annotation surfaced in `kmpTargetsInfo`/`kmpTargetsDoctor` (issue #81).
 *
 * The plugin must not hard-depend on any ABI tool (BCV / Kotlin's built-in `abiValidation`), but
 * the *evidence* is filesystem-visible: a committed dump directory (`api/` by default) whose
 * contents name targets the current selection may not have registered. When
 * `apiDump`/`updateKotlinAbi` runs under a narrowed selection it regenerates the dump from only the
 * registered targets — silently dropping or mis-inferring the rest — and
 * `apiCheck`/`checkKotlinAbi` validates only the registered subset, so drift on an unregistered
 * target is a false-green. This module turns that evidence into a set difference; the report
 * formatters turn the difference into a line.
 *
 * Everything here is pure over a [File]/[String], so the decision functions are unit-testable
 * without Gradle and the report tasks stay configuration-cache safe (the directory is read in the
 * always- rerun `@TaskAction`, never captured at configuration time).
 */

/**
 * The targets a committed dump directory covers, derived **layout-agnostically** so it works for
 * both the merged-dump tooling (a single `<project>.klib.api` with a `// Targets: […]` header) and
 * any legacy per-target directory layout (`api/<target>/`):
 * 1. every immediate sub*directory* name under [apiDir] (legacy per-target dumps), and
 * 2. every target named in the `// Targets: […]` header of each `*.klib.api` file.
 *
 * The flat JVM dump (`*.api`, not `*.klib.api`) deliberately contributes *presence only*, never a
 * named target: it carries no target label, and a jvm leaf renamed via `targetName` (#49) makes
 * mapping `<project>.api` back to a Gradle target name ambiguous. Naming it would risk a false gap;
 * omitting it only costs the jvm row, which is host-buildable and rarely the narrowed-away target.
 *
 * Returns an empty set when [apiDir] is absent or not a directory — the report then shows no ABI
 * section at all.
 */
internal fun readAbiDumpCoveredTargets(apiDir: File): Set<String> {
    if (!apiDir.isDirectory) return emptySet()
    val covered = sortedSetOf<String>()
    apiDir.listFiles()?.forEach { entry ->
        when {
            entry.isDirectory -> covered += entry.name
            entry.isFile && entry.name.endsWith(".klib.api") ->
                covered += parseKlibApiTargets(entry.readText())
        }
    }
    return covered
}

/**
 * The Gradle target names a merged KLib ABI dump covers, read from its `// Targets: […]` header —
 * the line the tooling writes at the top of `<project>.klib.api` listing every target merged into
 * the file (e.g. `// Targets: [iosArm64, js, linuxX64]`). Pure over the file text, tolerant of
 * absence: returns an empty set when no such header is present.
 */
internal fun parseKlibApiTargets(text: String): Set<String> {
    val header =
        text.lineSequence().firstOrNull { TARGETS_HEADER.containsMatchIn(it) } ?: return emptySet()
    val inside = TARGETS_HEADER.find(header)?.groupValues?.get(1) ?: return emptySet()
    return inside.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSortedSet()
}

/**
 * The dump-covered targets the current selection did **not** register — the coverage gap, as a
 * sorted list. Compared against the registered **`gradleName`s** (not leaf ids) on purpose: that is
 * what resolves `api/desktop/` correctly for a jvm leaf renamed via `targetName` (#49) and honestly
 * flags a pre-rename `api/jvm/` as orphaned. Naturally empty under a full selection (everything
 * registers, host-blind), so the annotation has zero noise in the common case — the
 * partial-selection gate the issue asked for falls straight out of the set difference.
 */
internal fun abiDumpUncovered(
    coveredTargetNames: Set<String>,
    registeredGradleNames: Set<String>,
): List<String> = (coveredTargetNames - registeredGradleNames).sorted()

private val TARGETS_HEADER = Regex("""//\s*Targets:\s*\[([^]]*)]""")
