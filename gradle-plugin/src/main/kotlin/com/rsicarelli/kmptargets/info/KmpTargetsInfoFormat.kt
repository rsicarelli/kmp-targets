package com.rsicarelli.kmptargets.info

/**
 * Renders the `kmpTargetsInfo` report from primitives only — no `Project`, extension, or
 * `KmpTargetSet` ever reaches this function, which is what keeps the task action trivially
 * configuration-cache safe (and this formatter unit-testable without Gradle).
 *
 * Every empty case is spelled out rather than printed as a bare blank: an undeclared `supports`, a
 * selection narrowed to nothing, and a genuinely disjoint `selection ∩ supported` each get a
 * distinct explanation, because "what will build and why" is the whole point of the report.
 */
internal fun formatKmpTargetsInfo(
    projectPath: String,
    selectionIds: List<String>,
    supportedIds: List<String>,
    supportsDeclared: Boolean,
    registeredIds: List<String>,
    originLabel: String,
    presetNames: List<String>,
    leafIds: List<String>,
): String = buildString {
    appendLine("kmp-targets — $projectPath")
    appendLine()
    appendLine("Selection (what to build now)")
    appendLine("  targets:  ${idsOrNone(selectionIds, "the selection narrows to nothing")}")
    appendLine("  source:   $originLabel")
    appendLine()
    appendLine("Supported (what this module can build)")
    if (supportsDeclared) {
        appendLine("  declared: yes")
        appendLine("  targets:  ${idsOrNone(supportedIds, "the declared set is empty")}")
    } else {
        appendLine(
            "  declared: no — this module never called supports { }; it registers no targets"
        )
    }
    appendLine()
    appendLine("Registered (selection ∩ supported)")
    appendLine("  targets:  ${registeredOrNone(registeredIds, selectionIds, supportedIds)}")
    appendLine()
    appendLine("Vocabulary")
    appendLine("  presets:  ${presetNames.joinToString(", ")}")
    append("  leaves:   ${leafIds.joinToString(", ")} (${leafIds.size})")
}

private fun idsOrNone(ids: List<String>, emptyReason: String): String =
    if (ids.isEmpty()) "(none — $emptyReason)" else ids.joinToString(", ")

/**
 * The registered line names *why* nothing registers, in precedence order: an empty selection and an
 * empty supported set each have their own explanation; only when both sides are non-empty is the
 * emptiness a genuine disjunction.
 */
private fun registeredOrNone(
    registeredIds: List<String>,
    selectionIds: List<String>,
    supportedIds: List<String>,
): String =
    when {
        registeredIds.isNotEmpty() -> registeredIds.joinToString(", ")
        selectionIds.isEmpty() -> "(none — the selection narrows to nothing)"
        supportedIds.isEmpty() -> "(none — no supported targets declared)"
        else -> "(none — the selection matches nothing this module supports; nothing registers)"
    }
