package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.hierarchy.computeHierarchySpec
import com.rsicarelli.kmptargets.hierarchy.resolveHierarchyCollapseEnabled
import com.rsicarelli.kmptargets.hierarchy.resolveHierarchyTemplateEnabled
import com.rsicarelli.kmptargets.hierarchy.toTemplate
import com.rsicarelli.kmptargets.host.currentHostEnabled
import com.rsicarelli.kmptargets.host.currentHostLabel
import com.rsicarelli.kmptargets.host.enforceHostCompatibility
import com.rsicarelli.kmptargets.host.hostImpossibleIds
import com.rsicarelli.kmptargets.info.KmpTargetsInfoTask
import com.rsicarelli.kmptargets.info.OriginLabels
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.parser.ParseResult
import com.rsicarelli.kmptargets.parser.didYouMean
import com.rsicarelli.kmptargets.parser.parseKmpTargets
import com.rsicarelli.kmptargets.parser.presetNames
import com.rsicarelli.kmptargets.source.ConfigFileValueSource
import com.rsicarelli.kmptargets.source.ConfigKeys
import com.rsicarelli.kmptargets.source.EnvironmentVariableSource
import com.rsicarelli.kmptargets.source.GradlePropertySource
import com.rsicarelli.kmptargets.source.LocalPropertyValueSource
import com.rsicarelli.kmptargets.source.SelectionSource
import com.rsicarelli.kmptargets.source.composeSelectionSources
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.konan.target.HostManager

public class KmpTargetsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val ext = target.extensions.create("kmpTargets", KmpTargetsExtension::class.java)
        ext.defaultSelection.convention(KmpTargetSet.all)

        // Dedicated config files, each read once as a whole map (tracked config-cache inputs) and
        // validated against the key registry — a typo fails the build instead of silently no-oping
        // (Bazel-style, issue #30).
        val personal: Map<String, String>? = configFile(target, ConfigKeys.LOCAL_FILE)
        val committed: Map<String, String>? = configFile(target, ConfigKeys.COMMITTED_FILE)
        validateKnownKeys(personal, ConfigKeys.LOCAL_FILE)
        validateKnownKeys(committed, ConfigKeys.COMMITTED_FILE)

        // Global selection: what the user wants to build now. A blank/absent property means "not
        // overriding" → defer to `defaultSelection`. A non-blank property that
        // resolves to an empty set via minus operators (e.g. `jvm,-jvm`) is an explicit "build
        // nothing" and must be honored, not silently treated as the default-all selection (issue
        // #9). Keying off `isNotBlank` (rather than the parsed set's emptiness) is what
        // distinguishes the two cases. The first-non-null walk preserves the exact
        // `composeSelectionSources` semantics (lower layers stay unread once one wins) while also
        // capturing WHICH layer won, for `kmpTargetsInfo`'s origin report (issue #33).
        val winner: Pair<String, String>? =
            labeledSources(target, ConfigKeys.TARGETS, personal, committed).firstNotNullOfOrNull {
                (label, source) ->
                source.read()?.let { value -> label to value }
            }
        val raw: String? = winner?.second
        if (raw != null && raw.isNotBlank()) {
            ext.globalSelection = parseOrThrow(raw, ConfigKeys.TARGETS)
        }
        // A blank winner shadows lower layers but does not override the default, so it yields no
        // origin either — the report then falls through to the defaultSelection/built-in labels.
        val configOrigin: String? = winner?.takeIf { it.second.isNotBlank() }?.first

        // Global defaults for the hierarchy template and its collapse rule (issue #50), each read
        // once at apply time as a primitive.
        val globalHierarchyEnabled: Boolean? =
            hierarchyTemplateEnabledGlobally(target, personal, committed)
        val globalCollapseEnabled: Boolean? =
            hierarchyCollapseEnabledGlobally(target, personal, committed)

        // Strict mode (#34): opt-in promotion of the selection/host advisories to failures, read
        // once at apply time as a primitive Boolean (default off) so no `Project` is captured.
        val strict: Boolean = strictModeEnabled(target, personal, committed)

        // Eager registration: `supports { … }` in the build-script body registers `selection ∩
        // supported` immediately — no deferred (after-evaluate) pass, no timing wall. We hook it
        // via
        // `withPlugin(KGP)` so registration fires the moment both KGP and a `supports` declaration
        // are present (in either order); if KGP is never applied, nothing registers. This runs at
        // configuration time and is never held by a Task, so no `Project` leaks into the config
        // cache.
        ext.onSupports = {
            target.pluginManager.withPlugin(KGP_ID) {
                register(target, ext, globalHierarchyEnabled, globalCollapseEnabled, strict)
            }
        }

        registerInfoTask(target, ext, configOrigin)

        // Umbrella lifecycle tasks (#77): registered unconditionally — like kmpTargetsInfo — so an
        // inert or never-supports module still carries a no-op task and an unqualified
        // `./gradlew kmpCompileAll` from the root never 404s in any project. The eager register()
        // loop appends one dependency per registered leaf, so each ends up wired to exactly the
        // registered intersection. Stored on the extension because the loop runs later, under
        // withPlugin(KGP).
        ext.compileAllTask = registerUmbrella(target, COMPILE_ALL_TASK, "build", COMPILE_ALL_DESC)
        ext.testAllTask = registerUmbrella(target, TEST_ALL_TASK, "verification", TEST_ALL_DESC)
    }

    /**
     * Registers one umbrella lifecycle task — a pure aggregator with no `@TaskAction` and no
     * inputs; the eager [register] loop wires its dependencies. Registration is unconditional and
     * lazy: when the task is never requested it is never configured, and when a module registers
     * nothing it stays a clean no-op. Config-cache safe by construction — `dependsOn` added later
     * is configuration-time wiring, and the task captures no `Project` and serializes no state.
     */
    private fun registerUmbrella(
        target: Project,
        name: String,
        group: String,
        description: String,
    ): TaskProvider<Task> =
        target.tasks.register(name) { task ->
            task.group = group
            task.description = description
        }

    /**
     * Wires the umbrella tasks (#77) for one freshly registered [leaf]/[kotlinTarget], off the live
     * `KotlinTarget` so the dependency is the genuine task — rename-proof and impossible to
     * silently zero-match, the exact failure modes hardcoded CI task lists suffer.
     *
     * For every non-Android target KGP creates the `main` (and `test`) compilation synchronously
     * when the target is created, so the real **compile** task is wired via a provider
     * ([KotlinCompilation.compileTaskProvider]) — a genuine `TaskProvider` that can never silently
     * zero-match. The **test-run** task exists only for the [KotlinTargetWithTests] targets (a
     * device-only native such as `iosArm64` has none, and is correctly skipped); its public surface
     * does not expose the execution task, so it is wired by its stable KGP name `<targetName>Test`
     * — still rename-proof, because `targetName` is the real registered name (`desktopTest`, never
     * a hardcoded `jvmTest`). Android is the exception — its compilations/test tasks are
     * variant-named and created later by AGP — so its tasks are named by reconstruction from the
     * fixed `android` gradle name, matching the literals hardcoded CI already used.
     *
     * The umbrellas only ever depend on registered *platform* tasks; the commonMain metadata
     * compilation is never referenced, so they cannot re-introduce the inert (#71) or
     * JVM-less-fragment (#72) failures.
     */
    private fun wireUmbrellas(
        ext: KmpTargetsExtension,
        leaf: KmpTarget,
        kotlinTarget: KotlinTarget,
    ) {
        val compileAll = ext.compileAllTask
        val testAll = ext.testAllTask
        if (leaf == KmpTarget.Jvm.Android) {
            // AGP materializes both production variants for a library/application module; a literal
            // dependsOn(name) fails loudly if one is absent — loud beats the silent zero-match.
            val cap = kotlinTarget.targetName.replaceFirstChar(Char::titlecase)
            compileAll?.configure {
                it.dependsOn("compileDebugKotlin$cap", "compileReleaseKotlin$cap")
            }
            testAll?.configure { it.dependsOn("testDebugUnitTest", "testReleaseUnitTest") }
            return
        }
        compileAll?.configure {
            it.dependsOn(
                kotlinTarget.compilations.named(KotlinCompilation.MAIN_COMPILATION_NAME).flatMap {
                    compilation ->
                    compilation.compileTaskProvider
                }
            )
        }
        if (kotlinTarget is KotlinTargetWithTests<*, *>) {
            testAll?.configure { it.dependsOn("${kotlinTarget.targetName}Test") }
        }
    }

    /**
     * Registers the `kmpTargetsInfo` introspection task (issue #33): one task that answers "what
     * did the plugin decide for this module, and why?".
     *
     * Timing under the eager model: registration happens at apply time, but `supports { … }` only
     * unions the supported set while the build-script body runs. The selection/supported/registered
     * inputs are therefore wired as providers that map straight to sorted-id `String` lists —
     * Gradle realizes a scheduled task's property providers at task-graph calculation, after the
     * whole body ran, so they observe the final state and only primitives are serialized into the
     * configuration cache. Registration is lazy on purpose: when the task is not requested it is
     * never configured, so the providers never realize and nothing here can leak into the cache.
     *
     * The providers call only `resolvedSelection()`/`resolvedSupported()` — pure reads that never
     * fire `onSupports` — so the report can never register targets as a side effect. It is also
     * registered unconditionally (not gated on KGP): without KGP or `supports`, the report states
     * explicitly that nothing is declared and nothing registers.
     */
    private fun registerInfoTask(target: Project, ext: KmpTargetsExtension, configOrigin: String?) {
        val projectPath = target.path
        target.tasks.register("kmpTargetsInfo", KmpTargetsInfoTask::class.java) { task ->
            task.group = "help"
            task.description =
                "Prints the resolved kmp-targets selection, its origin, the supported set, and " +
                    "the registered intersection for this project."
            task.projectPath.set(projectPath)
            task.presetNames.set(presetNames)
            task.leafIds.set(KmpTarget.all.map { it.id }.sorted())
            task.deprecatedIds.set(KmpTarget.deprecated.map { it.id }.sorted())
            task.selectionIds.set(target.provider { ids(ext.resolvedSelection()) })
            task.supportedIds.set(target.provider { ids(ext.resolvedSupported()) })
            task.supportsDeclared.set(target.provider { ext.supportsProperty.isPresent })
            task.registeredIds.set(
                target.provider { ids(ext.resolvedSelection() intersect ext.resolvedSupported()) }
            )
            // Host annotations (#44): same decision source as the host advisory, but guarded by a
            // runtime KGP check INSIDE the provider — one wiring path that degrades to empty
            // without KGP, and `HostManager` (touched only in host/HostCompatibility.kt bodies)
            // never classloads on that branch. `hasPlugin` is read at provider realization
            // (task-graph calc, post-body), so KGP applied after this plugin still counts.
            task.hostImpossibleIds.set(
                target.provider {
                    if (!target.pluginManager.hasPlugin(KGP_ID)) emptyList()
                    else
                        hostImpossibleIds(
                            ext.resolvedSelection() intersect ext.resolvedSupported(),
                            currentHostEnabled(),
                        )
                }
            )
            task.hostLabel.set(
                target.provider {
                    if (!target.pluginManager.hasPlugin(KGP_ID)) "" else currentHostLabel()
                }
            )
            // The rename annotation (issue #49) reads lazily for the same post-body reason as the
            // selection providers: the body sets `targetName(...)` before `supports { }`.
            task.jvmRegisteredAs.set(target.provider { ext.jvmTargetName.orNull })
            // Android-skip annotation (#51): same decision source as the advisory
            // (`shouldWarnAndroidWithoutAgp`), realized lazily post-body and guarded by the same
            // runtime KGP check as the host data — without KGP nothing registers, so there is no
            // skip to report. AGP presence is read at provider realization, so AGP applied
            // anywhere in the body still counts.
            task.androidWithoutAgp.set(
                target.provider {
                    target.pluginManager.hasPlugin(KGP_ID) &&
                        shouldWarnAndroidWithoutAgp(
                            ext.resolvedSelection() intersect ext.resolvedSupported(),
                            isAndroidPluginApplied(target),
                        )
                }
            )
            // Inert annotation (#71): same decision source as the advisory
            // (`shouldWarnInertModule` over `registered()`), realized lazily post-body and guarded
            // by the same runtime KGP check as the host data — without KGP no metadata compilation
            // exists, so there is no trap to report.
            task.inertModule.set(
                target.provider {
                    target.pluginManager.hasPlugin(KGP_ID) &&
                        shouldWarnInertModule(ext.supportsProperty.isPresent, ext.registered())
                }
            )
            // Native-only-metadata annotation (#72): same decision source as the advisory
            // (`shouldWarnNativeOnlyMetadata` over `resolvedSupported()`/`registered()`), realized
            // lazily post-body and guarded by the same runtime KGP check — without KGP nothing
            // registers, so no JVM-less fragment exists and there is no trap to report.
            task.nativeOnlyMetadata.set(
                target.provider {
                    target.pluginManager.hasPlugin(KGP_ID) &&
                        shouldWarnNativeOnlyMetadata(ext.resolvedSupported(), ext.registered())
                }
            )
            // The config-layer origin is fixed at apply time, but the fallback labels must read
            // `defaultSelection` lazily — the body may override it after apply.
            task.originLabel.set(
                target.provider {
                    configOrigin
                        ?: if (ext.defaultSelection.get() == KmpTargetSet.all) {
                            OriginLabels.DEFAULT_BUILTIN
                        } else {
                            OriginLabels.DEFAULT_BUILD_LOGIC
                        }
                }
            )
        }
    }

    internal companion object {
        const val KGP_ID: String = "org.jetbrains.kotlin.multiplatform"

        const val COMPILE_ALL_TASK: String = "kmpCompileAll"
        const val TEST_ALL_TASK: String = "kmpTestAll"

        const val COMPILE_ALL_DESC: String =
            "Compiles every target this module registered — the registered intersection, not a " +
                "hardcoded leaf list — so it stays correct under any selection and a renamed jvm " +
                "leaf. Excludes the commonMain metadata compilation, so it never re-introduces the " +
                "inert (#71) or JVM-less-fragment (#72) failures."

        const val TEST_ALL_DESC: String =
            "Runs the tests of every target this module registered that has a test task — " +
                "selection-agnostic and rename-proof, so a renamed jvm leaf's tests still run " +
                "where a hardcoded jvmTest would silently match nothing."
    }

    /**
     * The full precedence chain for one global config key (highest first): CLI `-P` →
     * `ORG_GRADLE_PROJECT_<key>` env → personal file → committed file → `gradle.properties` →
     * `local.properties` — each layer paired with the [OriginLabels] name `kmpTargetsInfo` reports
     * when that layer wins. Both global knobs read through this one chain (via [configSources]), so
     * they always share the same precedence, and origin reporting can never drift from value
     * resolution. Adding a future config layer means adding exactly one entry here.
     *
     * The top of the chain is decomposed by hand: `providers.gradleProperty` fuses CLI, env, and
     * `gradle.properties` into one provider with no origin information, but the dedicated files
     * must slot *between* env and `gradle.properties` (the consolidation point beats legacy loose
     * keys, issue #30). So CLI is read eagerly from the start parameter (a config-cache input — any
     * `-P` change invalidates the entry) and env explicitly via Gradle's native
     * `ORG_GRADLE_PROJECT_` mapping; `gradleProperty` then serves as the `gradle.properties` layer.
     * It still re-matches CLI/env values, but the first-non-null composition has already caught
     * those above, so the overlap is harmless. Documented nuance: `-Dorg.gradle.project.<key>` and
     * `~/.gradle/gradle.properties` resolve at that layer too, i.e. below the dedicated files —
     * which is why that layer's origin label names the whole fused group.
     */
    private fun labeledSources(
        target: Project,
        key: String,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): List<Pair<String, SelectionSource>> {
        val cli: String? = target.gradle.startParameter.projectProperties[key]
        return listOf(
            OriginLabels.cli(key) to SelectionSource { cli },
            OriginLabels.environmentVariable(key) to
                EnvironmentVariableSource(target.providers, ConfigKeys.ENV_PREFIX + key),
            ConfigKeys.LOCAL_FILE to SelectionSource { personal?.get(key) },
            ConfigKeys.COMMITTED_FILE to SelectionSource { committed?.get(key) },
            OriginLabels.gradleProperties(key) to GradlePropertySource(target.providers, key),
            OriginLabels.LOCAL_PROPERTIES to localPropertiesSource(target, key),
        )
    }

    /** The [labeledSources] chain composed into a plain first-non-null value source. */
    private fun configSources(
        target: Project,
        key: String,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): SelectionSource =
        composeSelectionSources(
            *labeledSources(target, key, personal, committed).map { it.second }.toTypedArray()
        )

    /**
     * Reads the global `kmptargets.hierarchyTemplate` flag through the standard [configSources]
     * chain. `null` means "not set — use the built-in default"; anything other than `true`/`false`
     * (case-insensitive) is treated as unset.
     */
    private fun hierarchyTemplateEnabledGlobally(
        target: Project,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): Boolean? =
        configSources(target, ConfigKeys.HIERARCHY_TEMPLATE, personal, committed)
            .read()
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull()

    /**
     * Reads the global `kmptargets.hierarchyCollapse` flag (issue #50) through the standard
     * [configSources] chain, with the same `null` = "not set" semantics as the template flag.
     */
    private fun hierarchyCollapseEnabledGlobally(
        target: Project,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): Boolean? =
        configSources(target, ConfigKeys.HIERARCHY_COLLAPSE, personal, committed)
            .read()
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull()

    /**
     * Reads the global `kmptargets.strict` flag through the standard [configSources] chain.
     * Deliberately opt-in: unset — or anything other than `true`/`false` (case-insensitive, per
     * `toBooleanStrictOrNull`) — means OFF, the advisory-only behavior.
     */
    private fun strictModeEnabled(
        target: Project,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): Boolean =
        configSources(target, ConfigKeys.STRICT, personal, committed)
            .read()
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull() ?: false

    /** The parsed entries of a dedicated config file in the root directory, or `null` if absent. */
    private fun configFile(target: Project, fileName: String): Map<String, String>? =
        target.providers
            .of(ConfigFileValueSource::class.java) {
                it.parameters.rootDir.set(target.rootDir)
                it.parameters.fileName.set(fileName)
            }
            .orNull

    /**
     * Fails the build when a dedicated config file carries a key outside [ConfigKeys.ALL] — a typo
     * must not silently no-op (Bazel's "fail loud on unknown key" parity, issue #30).
     */
    private fun validateKnownKeys(entries: Map<String, String>?, fileName: String) {
        if (entries == null) return
        val unknown = entries.keys - ConfigKeys.ALL
        if (unknown.isEmpty()) return
        val described =
            unknown.sorted().joinToString(", ") { key ->
                val suggestion = didYouMean(key, ConfigKeys.ALL)
                if (suggestion != null) "'$key' (did you mean '$suggestion'?)" else "'$key'"
            }
        throw GradleException(
            "$fileName: unknown key(s) $described. " +
                "Known keys: ${ConfigKeys.ALL.sorted().joinToString(", ")}"
        )
    }

    private fun localPropertiesSource(target: Project, propertyName: String): SelectionSource {
        val provider =
            target.providers.of(LocalPropertyValueSource::class.java) {
                it.parameters.rootDir.set(target.rootDir)
                it.parameters.propertyName.set(propertyName)
            }
        return SelectionSource { provider.orNull }
    }

    private fun parseOrThrow(raw: String, propertyName: String): KmpTargetSet =
        when (val r = parseKmpTargets(raw)) {
            is ParseResult.Ok -> r.set
            is ParseResult.Err -> throw GradleException("$propertyName: ${r.message}")
        }

    /**
     * Registers `selection ∩ supported`, emits the empty-overlap and host-impossible advisories,
     * and applies the minimal hierarchy template — all off the cumulative supported set, so
     * repeated `supports` calls only register the delta (idempotent), each host warning names a
     * leaf at most once, and the template still applies at most once.
     *
     * When [strict] is on (#34), the advisories fail the build instead of warning — with the
     * identical message text. Severity changes; policy does not: `shouldWarnEmptyOverlap`,
     * `shouldWarnAndroidWithoutAgp`, `impossibleOnHost`, and `freshDeprecated` stay the single
     * source of truth for *whether* to flag. What registers is never affected — with one deliberate
     * exception: `androidTarget` without an Android Gradle plugin is skipped in both modes (#51),
     * because registering it is a hard KGP failure, not a choice.
     */
    private fun register(
        project: Project,
        ext: KmpTargetsExtension,
        globalHierarchyEnabled: Boolean?,
        globalCollapseEnabled: Boolean?,
        strict: Boolean,
    ) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val selection = ext.resolvedSelection()
        val supported = ext.resolvedSupported()
        val active = selection intersect supported
        // Read once per registration pass, as a plain String — config-cache safe (issue #49). The
        // `targetName` sugar guarantees this was set before the jvm leaf registered.
        val jvmName: String? = ext.jvmTargetName.orNull
        // Read fresh on every supports{} pass, so AGP applied between two calls counts for the
        // later pass.
        val androidPluginApplied = isAndroidPluginApplied(project)
        (active.members - ext.registeredLeaves).forEach { leaf ->
            // KGP's androidTarget() reports a FATAL diagnostic (AndroidGradlePluginIsMissing,
            // thrown immediately) when no Android Gradle plugin is applied. Skip the leaf instead
            // — the advisory below names the module and the fix — and do NOT record it, so a
            // later supports{} pass after AGP is applied still registers it.
            if (leaf == KmpTarget.Jvm.Android && !androidPluginApplied) return@forEach
            val kotlinTarget = registerTarget(kotlin, leaf, jvmName)
            val gradleName = kotlinTarget.targetName
            // Wire the umbrella tasks (#77) off the live target before recording: the dependency is
            // the genuine compile/test task, so it is rename-proof and never a silent zero-match.
            wireUmbrellas(ext, leaf, kotlinTarget)
            // Leaf first, then the carrier+callbacks (issue #52): an onRegistered action that
            // re-enters register() must already see this leaf as registered.
            ext.registeredLeaves.add(leaf)
            ext.recordRegistration(leaf, gradleName)
        }

        // The selection is non-empty yet genuinely disjoint from the supported set, so nothing
        // registers. Keyed off the actual overlap (not "nothing registered"), so the message can
        // never name a token present in both lists (issue #10). Safe to fail here under strict:
        // the intersection is empty, so no target half-registered before the throw.
        if (shouldWarnEmptyOverlap(selection, supported)) {
            warnOrFail(strict, emptyOverlapWarning(project.path, selection, supported)) {
                project.logger.warn(it)
            }
        }

        // Android-without-AGP advisory (#51): the one family member that filters — androidTarget
        // genuinely did not register (the loop above skipped it; the alternative is KGP's raw
        // FATAL), so the message says exactly that. Mutually exclusive with empty-overlap (android
        // active ⇒ the overlap is non-empty). Ordered before host/deprecated: under strict,
        // "an asked-for target was NOT registered" beats "registered but uncompilable here" beats
        // "registered but deprecated". Deduped once per module via the boolean flag (a set would
        // be overkill for a single leaf), recorded AFTER warnOrFail (mirroring the host step) so
        // a strict failure leaves no bookkeeping behind.
        if (!ext.androidAgpWarned && shouldWarnAndroidWithoutAgp(active, androidPluginApplied)) {
            warnOrFail(strict, androidWithoutAgpWarning(project.path)) { project.logger.warn(it) }
            ext.androidAgpWarned = true
        }

        // Host-awareness (#32): names registered native targets this host cannot compile, never
        // changing what registers (the set stays identical across hosts). HostManager is touched
        // only here, inside `withPlugin(KGP_ID)` at configuration time, so its classes never load
        // when KGP is absent and nothing host-derived is held by a task action. Deduped per leaf
        // via `hostWarned`, so a later `supports` union that introduces a new impossible leaf
        // still flags — for that leaf only. The decision lives behind an enabled-set parameter
        // (`enforceHostCompatibility`) so strict-mode tests can simulate any host.
        ext.hostWarned +=
            enforceHostCompatibility(
                path = project.path,
                active = active,
                enabled = HostManager().enabled.toSet(),
                host = HostManager.host,
                alreadyWarned = ext.hostWarned,
                strict = strict,
            ) {
                project.logger.warn(it)
            }

        // Deprecation advisory (#43): Kotlin's docs mark leaves deprecated but KGP (2.3.21) emits
        // no configuration-time signal when they register, so this plugin does. Keyed off the
        // ACTIVE set — a deprecated leaf the module doesn't support never registers, so it is
        // never flagged. Signal only, never filtering; deduped per leaf via `deprecatedWarned`,
        // recorded AFTER warnOrFail (mirroring the host step) so a strict failure leaves no
        // bookkeeping behind. Ordered after the host advisory: under strict, "this machine can't
        // build it" beats "the ecosystem is sunsetting it". If a future KGP version emits its own
        // deprecation warning, delete this advisory.
        val freshDeprecated = freshDeprecated(active, ext.deprecatedWarned)
        if (freshDeprecated.isNotEmpty()) {
            warnOrFail(
                strict,
                deprecatedTargetsWarning(
                    project.path,
                    KmpTargetSet.of(*freshDeprecated.toTypedArray()),
                ),
            ) {
                project.logger.warn(it)
            }
            ext.deprecatedWarned += freshDeprecated
        }

        // Inert-module advisory (#71): the pass ended with zero registrations for a module that
        // declared supports { } — KGP still materializes the commonMain metadata compilation,
        // which fails on a platform-less module, so the doomed task deserves a signal. Keyed off
        // registered() — the exact query the message tells build-logic to gate on — so it covers
        // every cause uniformly: a disjoint overlap, an explicitly-empty selection (where
        // empty-overlap is silent by design, issue #9), and an android-only overlap skipped by the
        // #51 guard. Ordered LAST on purpose: under strict the cause advisories above (empty-
        // overlap, android-without-AGP) win the exception, and inert — the consequence channel —
        // is the thrown text only where no cause advisory covers the state. Deduped once per
        // module (registration is one-way, so a module that un-inerts can never re-inert),
        // recorded AFTER warnOrFail so a strict failure leaves no bookkeeping behind.
        if (
            !ext.inertWarned &&
                shouldWarnInertModule(ext.supportsProperty.isPresent, ext.registered())
        ) {
            warnOrFail(strict, inertModuleWarning(project.path)) { project.logger.warn(it) }
            ext.inertWarned = true
        }

        // Native-only-metadata advisory (#72): the module SUPPORTS the JVM family (androidTarget
        // and/or jvm) yet this pass ends with no JVM-family leaf registered while OTHER targets
        // did register. The module is alive — the platform klibs compile — but commonMain
        // collapses to a JVM-less shared fragment, so the *KotlinMetadata* compilations reject
        // JVM-flavored constructs (@JvmInline and friends): klibs build, metadata fails. Keyed off
        // resolvedSupported() (not the active overlap), so an android leaf the #51 guard skipped
        // still counts as "supports JVM, registered none". The registered-non-empty conjunct makes
        // this disjoint from inert (#71, registered empty) by construction — exactly one of the
        // two consequence advisories fires per module state, so their relative order can never
        // matter under strict; inert stays textually first (whole-module-dead before
        // fragment-flavor). Like inert, it is a consequence channel evaluated last: under strict
        // the cause advisories above (empty-overlap, android-without-AGP) win the exception.
        // Deduped once per module, recorded AFTER warnOrFail so a strict failure leaves no
        // bookkeeping behind.
        if (
            !ext.nativeOnlyMetadataWarned &&
                shouldWarnNativeOnlyMetadata(ext.resolvedSupported(), ext.registered())
        ) {
            warnOrFail(strict, nativeOnlyMetadataWarning(project.path)) { project.logger.warn(it) }
            ext.nativeOnlyMetadataWarned = true
        }

        // Deliberately receives the unfiltered active set even when android was skipped above:
        // android is ungrouped in the hierarchy taxonomy (it attaches straight to common and
        // never affects the native collapse), so filtering would change nothing.
        maybeApplyHierarchyTemplate(
            project,
            ext,
            active,
            globalHierarchyEnabled,
            globalCollapseEnabled,
        )
    }

    /**
     * Applies the minimal hierarchy template for [active], at most once per module. Calling
     * `applyHierarchyTemplate` is itself what suppresses KGP's costly default: KGP only
     * auto-applies its default when no template has been applied yet. When disabled (or the active
     * set is empty), we apply nothing and let KGP fall back to its default.
     */
    private fun maybeApplyHierarchyTemplate(
        project: Project,
        ext: KmpTargetsExtension,
        active: KmpTargetSet,
        globalEnabled: Boolean?,
        globalCollapseEnabled: Boolean?,
    ) {
        if (ext.hierarchyTemplateApplied || active.isEmpty()) return
        if (!resolveHierarchyTemplateEnabled(ext.hierarchyTemplate.orNull, globalEnabled)) return
        // The collapse knob (issue #50) only matters when the minimal template itself applies —
        // with the template disabled, KGP's default hierarchy owns the tree.
        val collapse =
            resolveHierarchyCollapseEnabled(ext.collapseHierarchy.orNull, globalCollapseEnabled)
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.applyHierarchyTemplate(computeHierarchySpec(active, collapse).toTemplate())
        ext.hierarchyTemplateApplied = true
    }

    /**
     * Registers [target] with KGP and returns the `KotlinTarget` the factory created. Its
     * [targetName][KotlinTarget.getTargetName] is the Gradle registration actually used — never
     * re-derived from the leaf, so the
     * [RegisteredTarget][com.rsicarelli.kmptargets.model.RegisteredTarget] carriers stay truthful
     * for the renamed jvm leaf (issue #49) and for whatever name KGP picks. The live target is also
     * what [wireUmbrellas] reads to find the genuine compile/test tasks (#77).
     */
    private fun registerTarget(
        kotlin: KotlinMultiplatformExtension,
        target: KmpTarget,
        jvmName: String?,
    ): KotlinTarget =
        when (target) {
            KmpTarget.Jvm.Android -> kotlin.androidTarget()
            // The only renamable leaf (issue #49): KGP's hierarchy matchers (`withJvm()`) key
            // off the platform type, so a custom-named jvm target attaches exactly like the
            // default.
            KmpTarget.Jvm.Desktop -> if (jvmName != null) kotlin.jvm(jvmName) else kotlin.jvm()
            KmpTarget.Native.Apple.Ios.Arm64 -> kotlin.iosArm64()
            KmpTarget.Native.Apple.Ios.SimulatorArm64 -> kotlin.iosSimulatorArm64()
            KmpTarget.Native.Apple.Ios.X64 -> kotlin.iosX64()
            KmpTarget.Native.Apple.Macos.Arm64 -> kotlin.macosArm64()
            KmpTarget.Native.Apple.Macos.X64 -> kotlin.macosX64()
            KmpTarget.Native.Apple.Watchos.Arm64 -> kotlin.watchosArm64()
            KmpTarget.Native.Apple.Watchos.Arm32 -> kotlin.watchosArm32()
            KmpTarget.Native.Apple.Watchos.X64 -> kotlin.watchosX64()
            KmpTarget.Native.Apple.Watchos.SimulatorArm64 -> kotlin.watchosSimulatorArm64()
            KmpTarget.Native.Apple.Watchos.DeviceArm64 -> kotlin.watchosDeviceArm64()
            KmpTarget.Native.Apple.Tvos.Arm64 -> kotlin.tvosArm64()
            KmpTarget.Native.Apple.Tvos.X64 -> kotlin.tvosX64()
            KmpTarget.Native.Apple.Tvos.SimulatorArm64 -> kotlin.tvosSimulatorArm64()
            KmpTarget.Native.Linux.X64 -> kotlin.linuxX64()
            KmpTarget.Native.Linux.Arm64 -> kotlin.linuxArm64()
            KmpTarget.Native.Mingw.X64 -> kotlin.mingwX64()
            KmpTarget.Native.AndroidNative.Arm32 -> kotlin.androidNativeArm32()
            KmpTarget.Native.AndroidNative.Arm64 -> kotlin.androidNativeArm64()
            KmpTarget.Native.AndroidNative.X86 -> kotlin.androidNativeX86()
            KmpTarget.Native.AndroidNative.X64 -> kotlin.androidNativeX64()
            KmpTarget.Web.Js ->
                kotlin.js {
                    browser()
                    nodejs()
                }
            KmpTarget.Web.WasmJs ->
                kotlin.wasmJs {
                    browser()
                    nodejs()
                }
            KmpTarget.Web.WasmWasi -> kotlin.wasmWasi { nodejs() }
        }
}

/**
 * Strict mode's single escalation point (#34): warn when [strict] is off — byte-for-byte the
 * advisory behavior — and fail with the **same** [message] when on. Severity changes; the decision
 * of *whether* to flag stays with the callers' policy functions.
 */
internal fun warnOrFail(strict: Boolean, message: String, warn: (String) -> Unit) {
    if (strict) throw GradleException(message)
    warn(message)
}

/**
 * Whether to emit [emptyOverlapWarning]: only when the selection is non-empty yet genuinely
 * disjoint from the supported set. Keyed off the actual overlap (not "nothing registered with
 * KGP"), so the message can never name a token present in both lists (issue #10).
 */
internal fun shouldWarnEmptyOverlap(selection: KmpTargetSet, supported: KmpTargetSet): Boolean =
    selection.isNotEmpty() && (selection intersect supported).isEmpty()

internal fun emptyOverlapWarning(
    path: String,
    selection: KmpTargetSet,
    supported: KmpTargetSet,
): String =
    "kmp-targets: '$path' supports ${ids(supported)} but the selection ${ids(selection)} " +
        "matches none of them — registering no targets for this module."

/**
 * KGP's own Android-plugin id list (`org.jetbrains.kotlin.gradle.utils.androidPluginIds`), mirrored
 * verbatim so the registration guard skips `androidTarget` on exactly the modules where KGP's
 * `androidTarget()` would fail — and never on ones (dynamic-feature, test, …) where it succeeds.
 * Pinned by a test against narrowing to the two common ids.
 */
internal val androidPluginIds: List<String> =
    listOf(
        "com.android.application",
        "com.android.library",
        "com.android.dynamic-feature",
        "com.android.test",
        "com.android.instantapp",
        "com.android.feature",
    )

/** Whether any Android Gradle plugin is applied to [project] — a cheap plugin-manager lookup. */
internal fun isAndroidPluginApplied(project: Project): Boolean = androidPluginIds.any {
    project.pluginManager.hasPlugin(it)
}

/**
 * Whether to emit [androidWithoutAgpWarning]: `androidTarget` is in the active (registering) set
 * but no Android Gradle plugin is applied, so the leaf cannot register — KGP's `androidTarget()` is
 * a FATAL diagnostic without AGP. Pure over its inputs, so it is unit-testable without Gradle; the
 * same decision drives the `kmpTargetsInfo` skipped annotation.
 */
internal fun shouldWarnAndroidWithoutAgp(
    active: KmpTargetSet,
    androidPluginApplied: Boolean,
): Boolean = KmpTarget.Jvm.Android in active.members && !androidPluginApplied

/**
 * The android-without-AGP advisory (#51). Mirrors its siblings in shape; serves as both the warning
 * and the strict-mode failure text through [warnOrFail]. Unlike them it reports a leaf that was
 * genuinely NOT registered, and names the fix — the ordering rule that the Android plugin must be
 * applied before `supports { }` runs.
 */
internal fun androidWithoutAgpWarning(path: String): String =
    "kmp-targets: '$path' selects and supports [androidTarget] but no Android Gradle plugin is " +
        "applied — the target was not registered. Apply com.android.library or " +
        "com.android.application before supports { }."

/**
 * The deprecated leaves of [active] not yet flagged for this module — the dedup math of the
 * deprecation advisory (#43), pure so it is unit-testable without Gradle. Keyed off the active
 * (registered) set on purpose: a deprecated leaf the module never registers is nobody's problem.
 */
internal fun freshDeprecated(active: KmpTargetSet, alreadyWarned: Set<KmpTarget>): Set<KmpTarget> =
    active.members.filterTo(mutableSetOf()) { it in KmpTarget.deprecated } - alreadyWarned

/**
 * Single aggregated deprecation advisory naming the [deprecated] ids (sorted). Mirrors
 * [emptyOverlapWarning]/`hostImpossibleWarning` in shape; serves as both the warning and the
 * strict-mode failure text through `warnOrFail`, so the two can never drift apart. The version
 * stamp must track the `KmpTarget.deprecated` overrides (see the model KDoc).
 */
internal fun deprecatedTargetsWarning(path: String, deprecated: KmpTargetSet): String =
    "kmp-targets: '$path' registers ${ids(deprecated)} which Kotlin marks deprecated " +
        "(since Kotlin 2.3.20, see https://kotlinlang.org/docs/native-target-support.html) — " +
        "still registered (selection is unchanged), but consider migrating off them."

/**
 * Whether to emit [inertModuleWarning] (#71): the module declared `supports { }` yet ended a
 * registration pass with zero actual registrations. Keyed off [registered] — the
 * `KmpTargetsExtension.registered()` snapshot, i.e. what truly registered with KGP — rather than
 * `selection ∩ supported`, so the condition is provably the same one the message tells build-logic
 * to gate on, and the #51 android skip counts as inert. Deliberately host-free: registration is
 * host-blind, so a registered-but-uncompilable module is NOT inert on any host. A module that never
 * declared `supports { }` registers nothing *intentionally* (explicit-selection doctrine) and is
 * never flagged. Pure over its inputs, so it is unit-testable without Gradle; the same decision
 * drives the `kmpTargetsInfo` inert line.
 */
internal fun shouldWarnInertModule(supportsDeclared: Boolean, registered: KmpTargetSet): Boolean =
    supportsDeclared && registered.isEmpty()

/**
 * The inert-module advisory (#71). Mirrors its siblings in shape; serves as both the warning and
 * the strict-mode failure text through [warnOrFail]. Unlike them it names a *task-level
 * consequence* — KGP materializes `compileCommonMainKotlinMetadata` for every module applying the
 * KMP plugin, and that compilation fails when no platform target exists — and the build-logic gate.
 * Deliberately set-free: the advisory is cause-agnostic (disjoint overlap, explicitly-empty
 * selection, android-only AGP skip all fire it), so naming the selection or supported sets would
 * mislead in at least one of those cases; the cause advisories above it name the sets.
 */
internal fun inertModuleWarning(path: String): String =
    "kmp-targets: '$path' declared supports { } but registered zero targets — the module is " +
        "inert. KGP still materializes the commonMain metadata compilation, which fails with no " +
        "platform targets. Gate it in build-logic when kmpTargets.registered().isEmpty()."

/**
 * Whether to emit [nativeOnlyMetadataWarning] (#72): the module supports the JVM family
 * ([KmpTargetSet.jvmFamily]) yet registered no JVM-family leaf while registering at least one other
 * target. Keyed off [supported] — `resolvedSupported()`, not the active overlap — so a
 * selected-but-skipped android leaf (the #51 guard) still counts as "supports JVM, registered
 * none"; and off [registered] — the actual registrations — which is leaf-based, so a jvm leaf
 * renamed via `targetName` (issue #49) still keeps the fragment JVM-flavored. The
 * registered-non-empty conjunct makes it disjoint from [shouldWarnInertModule] (#71, registered
 * empty): an inert module is whole-module-dead, this one is alive with a JVM-less commonMain. Any
 * single JVM-family leaf registered — android-only or jvm-only — defeats the predicate (Android is
 * a JVM platform). Deliberately host-free: registration is host-blind, and a JVM-less fragment is
 * JVM-less on every host. Pure over its inputs, so it is unit-testable without Gradle; the same
 * decision drives the `kmpTargetsInfo` jvm-less line.
 */
internal fun shouldWarnNativeOnlyMetadata(
    supported: KmpTargetSet,
    registered: KmpTargetSet,
): Boolean =
    (supported intersect KmpTargetSet.jvmFamily).isNotEmpty() &&
        registered.isNotEmpty() &&
        (registered intersect KmpTargetSet.jvmFamily).isEmpty()

/**
 * The native-only-metadata advisory (#72). Mirrors its siblings in shape; serves as both the
 * warning and the strict-mode failure text through [warnOrFail]. Like inert (#71) it names a
 * *task-level consequence* — here the paradoxical half-failure where every platform klib compiles
 * but the `*KotlinMetadata*` compilations reject JVM-flavored constructs — and it deliberately
 * names the greppable symptom (`@JvmInline`) so a user arriving from the raw compiler error finds
 * it. The gate it points at is the *scoped* sibling of inert's: disable only the metadata
 * compilations, keep the alive platform ones. Deliberately set-free: the advisory is cause-agnostic
 * (a narrowed lane and an android-only AGP skip fire it alike), so naming the selection or
 * supported sets would mislead in at least one of those cases; the cause advisories above it name
 * the sets.
 */
internal fun nativeOnlyMetadataWarning(path: String): String =
    "kmp-targets: '$path' supports the JVM family but this selection registered no JVM-family " +
        "target while other targets did — commonMain is now a JVM-less shared fragment. The " +
        "platform klibs compile, but the *KotlinMetadata* compilations reject JVM-flavored " +
        "constructs (e.g. @JvmInline): klibs build, metadata fails. Gate it in build-logic when " +
        "kmpTargets.registered(jvmFamily).isEmpty(), disabling only the *KotlinMetadata* " +
        "compilations."

private fun ids(set: KmpTargetSet): List<String> = set.members.map { it.id }.sorted()
