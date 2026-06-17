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
    hostImpossibleIds: List<String>,
    hostLabel: String,
    deprecatedIds: List<String>,
    jvmRegisteredAs: String? = null,
    androidWithoutAgp: Boolean = false,
    inertModule: Boolean = false,
    nativeOnlyMetadata: Boolean = false,
    frameworkDeclared: Boolean = false,
    frameworkBaseName: String = "",
    frameworkAttachedIds: List<String> = emptyList(),
    frameworkBuildTypes: List<String> = emptyList(),
    frameworkXcframework: Boolean = false,
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
    appendLine(
        "  targets:  " +
            annotated(registeredOrNone(registeredIds, selectionIds, supportedIds), registeredIds) {
                id ->
                val markers = buildList {
                    // The skip annotation (#51): the literal keeps this file pure-primitive; it
                    // is KmpTarget.Jvm.Android.id.
                    if (id == "androidTarget" && androidWithoutAgp) {
                        add("(skipped: no Android plugin applied)")
                    }
                    // The rename (issue #49) annotates the jvm leaf so the report explains why the
                    // build has e.g. `compileKotlinDesktop` tasks but no `jvm`-named target.
                    if (id == "jvm" && jvmRegisteredAs != null) {
                        add("(registered as: $jvmRegisteredAs)")
                    }
                    if (id in hostImpossibleIds) {
                        add("(not compilable on this host: $hostLabel)")
                    }
                }
                markers.takeIf { it.isNotEmpty() }?.joinToString(" ")
            }
    )
    // The inert line (#71) is its own row, not a per-leaf marker: it describes the whole module
    // (zero actual registrations → doomed commonMain metadata compilation), and it must also
    // render when the abstract `selection ∩ supported` line is non-empty but registration skipped
    // everything (the #51 android case).
    if (inertModule) {
        appendLine(
            "  inert:    zero targets registered — commonMain metadata compilation will fail; " +
                "gate on registered().isEmpty()"
        )
    }
    // The jvm-less line (#72) is likewise a whole-module row, mutually exclusive with inert by
    // construction (it requires registrations, inert requires none): targets registered, but no
    // JVM-family leaf among them, so the metadata compilations reject JVM-flavored constructs
    // while the platform klibs compile.
    if (nativeOnlyMetadata) {
        appendLine(
            "  jvm-less: no JVM-family target registered — *KotlinMetadata* compilations reject " +
                "@JvmInline etc. while the klibs compile; gate on registered(jvmFamily).isEmpty()"
        )
    }
    // The Apple framework section (#107) renders whenever a framework is declared — the same facts
    // kmpTargetsDoctor shows, from the same providers. An empty `attached` is the unattached state:
    // the framework will not build for this selection (the doctor explains the consequence + fix).
    if (frameworkDeclared) {
        appendLine()
        appendLine("Apple framework")
        appendLine("  name:        $frameworkBaseName")
        appendLine(
            "  attached:    " +
                idsOrNone(frameworkAttachedIds, "no Apple target in its scope registered")
        )
        appendLine("  buildTypes:  ${frameworkBuildTypes.joinToString(", ")}")
        appendLine("  xcframework: ${if (frameworkXcframework) "on" else "off"}")
    }
    appendLine()
    appendLine("Vocabulary")
    appendLine("  presets:  ${presetNames.joinToString(", ")}")
    append(
        "  leaves:   " +
            leafIds.joinToString(", ") { id ->
                if (id in deprecatedIds) "$id (deprecated)" else id
            } +
            " (${leafIds.size})"
    )
}

/**
 * Re-renders [ids] with a per-leaf [marker] suffix when any leaf earns one; falls back to the
 * pre-rendered [plain] line (which carries the explicit empty-case wording) when none does.
 */
private fun annotated(plain: String, ids: List<String>, marker: (String) -> String?): String =
    if (ids.none { marker(it) != null }) plain
    else ids.joinToString(", ") { id -> marker(id)?.let { "$id $it" } ?: id }

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
