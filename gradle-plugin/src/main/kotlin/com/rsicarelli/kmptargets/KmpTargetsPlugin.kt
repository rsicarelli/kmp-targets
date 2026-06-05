package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.hierarchy.computeHierarchySpec
import com.rsicarelli.kmptargets.hierarchy.resolveHierarchyTemplateEnabled
import com.rsicarelli.kmptargets.hierarchy.toTemplate
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.parser.ParseResult
import com.rsicarelli.kmptargets.parser.didYouMean
import com.rsicarelli.kmptargets.parser.parseKmpTargets
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
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

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
        // distinguishes the two cases.
        val raw: String? = configSources(target, ConfigKeys.TARGETS, personal, committed).read()
        if (raw != null && raw.isNotBlank()) {
            ext.globalSelection = parseOrThrow(raw, ConfigKeys.TARGETS)
        }

        // Global default for the hierarchy template, read once at apply time as a primitive.
        val globalHierarchyEnabled: Boolean? =
            hierarchyTemplateEnabledGlobally(target, personal, committed)

        // Eager registration: `supports { … }` in the build-script body registers `selection ∩
        // supported` immediately — no deferred (after-evaluate) pass, no timing wall. We hook it
        // via
        // `withPlugin(KGP)` so registration fires the moment both KGP and a `supports` declaration
        // are present (in either order); if KGP is never applied, nothing registers. This runs at
        // configuration time and is never held by a Task, so no `Project` leaks into the config
        // cache.
        ext.onSupports = {
            target.pluginManager.withPlugin(KGP_ID) {
                register(target, ext, globalHierarchyEnabled)
            }
        }
    }

    internal companion object {
        const val KGP_ID: String = "org.jetbrains.kotlin.multiplatform"
    }

    /**
     * The full precedence chain for one global config key (highest first): CLI `-P` →
     * `ORG_GRADLE_PROJECT_<key>` env → personal file → committed file → `gradle.properties` →
     * `local.properties`. Both global knobs read through this one chain, so they always share the
     * same precedence.
     *
     * The top of the chain is decomposed by hand: `providers.gradleProperty` fuses CLI, env, and
     * `gradle.properties` into one provider with no origin information, but the dedicated files
     * must slot *between* env and `gradle.properties` (the consolidation point beats legacy loose
     * keys, issue #30). So CLI is read eagerly from the start parameter (a config-cache input — any
     * `-P` change invalidates the entry) and env explicitly via Gradle's native
     * `ORG_GRADLE_PROJECT_` mapping; `gradleProperty` then serves as the `gradle.properties` layer.
     * It still re-matches CLI/env values, but the first-non-null composition has already caught
     * those above, so the overlap is harmless. Documented nuance: `-Dorg.gradle.project.<key>` and
     * `~/.gradle/gradle.properties` resolve at that layer too, i.e. below the dedicated files.
     */
    private fun configSources(
        target: Project,
        key: String,
        personal: Map<String, String>?,
        committed: Map<String, String>?,
    ): SelectionSource {
        val cli: String? = target.gradle.startParameter.projectProperties[key]
        return composeSelectionSources(
            SelectionSource { cli },
            EnvironmentVariableSource(target.providers, ConfigKeys.ENV_PREFIX + key),
            SelectionSource { personal?.get(key) },
            SelectionSource { committed?.get(key) },
            GradlePropertySource(target.providers, key),
            localPropertiesSource(target, key),
        )
    }

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
     * Registers `selection ∩ supported`, emits the empty-overlap advisory, and applies the minimal
     * hierarchy template — all off the cumulative supported set, so repeated `supports` calls only
     * register the delta (idempotent) and the template still applies at most once.
     */
    private fun register(
        project: Project,
        ext: KmpTargetsExtension,
        globalHierarchyEnabled: Boolean?,
    ) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val selection = ext.resolvedSelection()
        val supported = ext.resolvedSupported()
        val active = selection intersect supported
        (active.members - ext.registered).forEach { leaf ->
            registerTarget(kotlin, leaf)
            ext.registered.add(leaf)
        }

        // Advisory only: the selection is non-empty yet genuinely disjoint from the supported set,
        // so nothing registers. Keyed off the actual overlap (not "nothing registered"), so the
        // message can never name a token present in both lists (issue #10).
        if (shouldWarnEmptyOverlap(selection, supported)) {
            project.logger.warn(emptyOverlapWarning(project.path, selection, supported))
        }

        maybeApplyHierarchyTemplate(project, ext, active, globalHierarchyEnabled)
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
    ) {
        if (ext.hierarchyTemplateApplied || active.isEmpty()) return
        if (!resolveHierarchyTemplateEnabled(ext.hierarchyTemplate.orNull, globalEnabled)) return
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.applyHierarchyTemplate(computeHierarchySpec(active).toTemplate())
        ext.hierarchyTemplateApplied = true
    }

    private fun registerTarget(kotlin: KotlinMultiplatformExtension, target: KmpTarget) {
        when (target) {
            KmpTarget.Jvm.Android -> kotlin.androidTarget()
            KmpTarget.Jvm.Desktop -> kotlin.jvm()
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

private fun ids(set: KmpTargetSet): List<String> = set.members.map { it.id }.sorted()
