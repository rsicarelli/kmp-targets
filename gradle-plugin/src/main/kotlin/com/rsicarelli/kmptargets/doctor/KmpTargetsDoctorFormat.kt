package com.rsicarelli.kmptargets.doctor

/**
 * Renders the `kmpTargetsDoctor` report from primitives only — no `Project`, extension, or
 * `KmpTargetSet` ever reaches this function, which keeps the task action trivially
 * configuration-cache safe and this formatter unit-testable without Gradle (mirrors
 * [com.rsicarelli.kmptargets.info.formatKmpTargetsInfo]).
 *
 * Doctor is the *triage* surface to `kmpTargetsInfo`'s neutral *state dump*: every `[!]` finding is
 * keyed off a boolean the plugin computes with the **same** decision function the matching advisory
 * uses ([com.rsicarelli.kmptargets.shouldWarnInertModule],
 * [com.rsicarelli.kmptargets.shouldWarnNativeOnlyMetadata],
 * [com.rsicarelli.kmptargets.shouldWarnAndroidWithoutAgp], the host/deprecation predicates), so the
 * report can never re-detect or drift from what actually happened. The findings are emitted in the
 * same order [com.rsicarelli.kmptargets.KmpTargetsPlugin] emits the advisories — causes
 * (empty-overlap, android-without-AGP) before consequences (inert, JVM-less, single-target KSP) —
 * so cause reads above effect. The one finding with no advisory counterpart is `single-target KSP`
 * ([com.rsicarelli.kmptargets.shouldWarnSingleTargetKsp], #73): doctor-only, since whether the
 * module generates into commonMain is unobservable.
 *
 * The project-edge closure section (#80) is best-effort, project-edges only, and prints its two
 * permanent limits inline: it sees no external (non-`project(...)`) dependencies, and its
 * android→jvm fallback only approximates Gradle's attribute matching.
 */
internal fun formatKmpTargetsDoctor(
    projectPath: String,
    supportsDeclared: Boolean,
    selectionIds: List<String>,
    supportedIds: List<String>,
    registeredIds: List<String>,
    emptyOverlap: Boolean,
    inertModule: Boolean,
    nativeOnlyMetadata: Boolean,
    androidWithoutAgp: Boolean,
    hostImpossibleIds: List<String>,
    hostLabel: String,
    registeredDeprecatedIds: List<String>,
    singleTargetKsp: Boolean,
    jvmRegisteredAs: String?,
    closureDepCount: Int,
    closureGaps: List<ClosureGap>,
): String = buildString {
    appendLine("kmp-targets doctor — $projectPath")
    appendLine()

    // A module that never declared supports { } registers nothing *intentionally* (explicit-
    // selection doctrine) — never a problem, and there is nothing downstream to check.
    if (!supportsDeclared) {
        append(
            "✓ no kmp-targets selection — this module never called supports { }; registering " +
                "nothing is intentional (explicit-selection doctrine)"
        )
        return@buildString
    }

    val findings = buildList {
        if (emptyOverlap) {
            add(
                finding(
                    "selection matches nothing supported",
                    "why:    the selection ${render(selectionIds)} matches none of the supported " +
                        "set ${render(supportedIds)}",
                    "effect: this module registers no targets",
                    "fix:    widen the selection or this module's supports { } so they overlap",
                )
            )
        }
        if (androidWithoutAgp) {
            add(
                finding(
                    "androidTarget skipped",
                    "why:    androidTarget is selected and supported but no Android Gradle plugin " +
                        "is applied",
                    "effect: the target was not registered — KGP's androidTarget() is fatal " +
                        "without AGP",
                    "fix:    apply com.android.library/application before supports { }, gated on " +
                        "the selection",
                )
            )
        }
        if (hostImpossibleIds.isNotEmpty()) {
            add(
                finding(
                    "not compilable on this host",
                    "why:    ${render(hostImpossibleIds)} cannot be compiled on this host " +
                        "($hostLabel)",
                    "effect: still registered (selection is host-independent), but their " +
                        "compile/link tasks fail or are skipped here",
                    "fix:    run these targets' tasks on a capable host — the selection is " +
                        "unchanged",
                )
            )
        }
        if (registeredDeprecatedIds.isNotEmpty()) {
            add(
                finding(
                    "deprecated targets registered",
                    "why:    ${render(registeredDeprecatedIds)} are marked deprecated by Kotlin",
                    "effect: still registered (selection is unchanged), but consider migrating " +
                        "off them",
                    "fix:    drop them from this module's supports { } once migrated",
                )
            )
        }
        if (inertModule) {
            add(
                finding(
                    "inert module",
                    "why:    declared supports { } but registered zero targets",
                    "effect: KGP still materializes the commonMain metadata compilation, which " +
                        "fails with no platform targets",
                    "fix:    gate on registered().isEmpty() in build-logic",
                )
            )
        }
        if (nativeOnlyMetadata) {
            add(
                finding(
                    "JVM-less commonMain",
                    "why:    supports the JVM family but registered no JVM-family target while " +
                        "other targets did",
                    "effect: the platform klibs compile, but the *KotlinMetadata* compilations " +
                        "reject JVM-flavored constructs (e.g. @JvmInline)",
                    "fix:    gate on registered(jvmFamily).isEmpty() in build-logic, disabling " +
                        "only the *KotlinMetadata* compilations",
                )
            )
        }
        if (singleTargetKsp) {
            add(
                finding(
                    "single-target KSP",
                    "why:    a KSP plugin is applied but only one target is registered, so there " +
                        "is no shared commonMain metadata route (kspCommonMainKotlinMetadata " +
                        "needs two or more targets)",
                    "effect: a processor that generates into commonMain is skipped — commonMain " +
                        "references to generated symbols won't resolve",
                    "fix:    keep codegen-consuming code in the target source set, or register a " +
                        "second target",
                )
            )
        }
    }

    if (findings.isEmpty()) {
        appendLine(cleanBill(registeredIds))
    } else {
        findings.forEach {
            appendLine(it)
            appendLine()
        }
    }

    // The rename (#49) is context, not a problem: it explains why the build has e.g.
    // compileKotlinDesktop tasks but no `jvm`-named target.
    if (jvmRegisteredAs != null) {
        appendLine()
        appendLine("note: jvm (registered as: $jvmRegisteredAs)")
    }

    appendClosure(projectPath, closureDepCount, closureGaps)
}

private fun StringBuilder.appendClosure(
    projectPath: String,
    depCount: Int,
    gaps: List<ClosureGap>,
) {
    if (depCount == 0 && gaps.isEmpty()) return
    appendLine()
    appendLine("Project-edge closure (project dependencies only)")
    if (gaps.isEmpty()) {
        appendLine(
            "✓ $depCount project ${if (depCount == 1) "dependency" else "dependencies"}, no gaps"
        )
    } else {
        gaps.forEach { gap ->
            appendLine("[!] $projectPath → ${gap.dependencyPath}")
            appendLine(
                "    missing: ${render(gap.missingLeafIds)} — the dependency registers none of " +
                    "these leaves this module needs"
            )
            appendLine(
                "    fix:     widen ${gap.dependencyPath}'s supports { } (or the selection) to " +
                    "register them"
            )
        }
    }
    append(
        "  limits: external (non-project) dependencies are invisible; the android→jvm fallback is " +
            "approximate"
    )
}

private fun finding(title: String, vararg lines: String): String = buildString {
    appendLine("[!] $title")
    lines.forEachIndexed { i, line ->
        if (i == lines.lastIndex) append("    $line") else appendLine("    $line")
    }
}

private fun cleanBill(registeredIds: List<String>): String =
    if (registeredIds.isEmpty()) {
        "✓ no kmp-targets issues — no targets registered for this module"
    } else {
        "✓ clean bill of health — registered: ${registeredIds.joinToString(", ")}"
    }

private fun render(ids: List<String>): String =
    if (ids.isEmpty()) "(none)" else ids.joinToString(", ")
