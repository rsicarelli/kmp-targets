package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.hierarchy.computeHierarchySpec
import com.rsicarelli.kmptargets.hierarchy.resolveHierarchyTemplateEnabled
import com.rsicarelli.kmptargets.hierarchy.toTemplate
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.parser.ParseResult
import com.rsicarelli.kmptargets.parser.parseKmpTargets
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
        ext.fallback.convention(KmpTargetSet.all)

        // Global selection: what the user wants to build now. A blank/absent property means "not
        // overriding" → fallback. A non-blank property that resolves to an empty set via minus
        // operators (e.g. `jvm,-jvm`) is an explicit "build nothing" and must be honored, not
        // silently treated as the default-all fallback (issue #9). Keying off `isNotBlank` (rather
        // than the parsed set's emptiness) is what distinguishes the two cases.
        val raw: String? = selectionSources(target).read()
        if (raw != null && raw.isNotBlank()) {
            ext.selection.convention(parseOrThrow(raw, KMP_TARGETS))
        } else {
            ext.selection.convention(ext.fallback)
        }

        // Per-module supported set declared via the `kmptargets.supported` property (the manual
        // escape hatch for modules that don't apply a convention plugin). Convention plugins
        // declare
        // theirs through KmpTargets.declareSupported instead.
        supportedSources(target).read()?.let { rawSupported ->
            ext.accumulateSupported(parseOrThrow(rawSupported, SUPPORTED_PROPERTY))
        }

        // Register selection ∩ supported once KGP is present. Queued here so that, in the
        // convention
        // flow (core applied before KGP), the accumulated supported set is already visible when
        // this
        // fires. Idempotent, so it composes with the per-declaration passes in declareSupported.
        target.pluginManager.withPlugin(KGP_ID) { KmpTargets.registerResolved(target, ext) }

        // Global default for the hierarchy template, read at apply time and pulled out as a
        // primitive so the afterEvaluate closure below never captures `Project` (config-cache
        // safe).
        val globalHierarchyEnabled: Boolean? = hierarchyTemplateEnabledGlobally(target)

        // Two things deferred to afterEvaluate, for the same reason: the final active set is only
        // known after every convention plugin has declared its supported slice (under composition
        // .mobile + .web an earlier pass is transiently incomplete), and there is no public
        // apply-time "all plugins applied" hook. Target *registration* stays eager; only these —
        // the
        // advisory warning and the hierarchy template, both functions of the final active set —
        // wait.
        // The closure captures only `ext` (immutable sets + booleans) and the primitive flag, so it
        // stays configuration-cache safe.
        target.afterEvaluate { p ->
            if (!p.plugins.hasPlugin(KGP_ID)) return@afterEvaluate
            val selection = ext.selection.get()
            val active = selection intersect ext.effectiveSupported()

            // Advisory only: the selection is non-empty yet genuinely disjoint from the supported
            // set, so nothing registers. Keyed off the actual overlap (not "nothing registered"),
            // so the message can never name a token present in both lists (issue #10).
            if (KmpTargets.shouldWarnEmptyOverlap(selection, ext.effectiveSupported())) {
                p.logger.warn(
                    KmpTargets.emptyOverlapWarning(p.path, selection, ext.effectiveSupported())
                )
            }

            KmpTargets.maybeApplyHierarchyTemplate(p, ext, active, globalHierarchyEnabled)
        }
    }

    private fun selectionSources(target: Project): SelectionSource =
        composeSelectionSources(
            GradlePropertySource(target.providers, KMP_TARGETS),
            EnvironmentVariableSource(target.providers, KMP_TARGETS),
            localPropertiesSource(target, KMP_TARGETS),
        )

    private fun supportedSources(target: Project): SelectionSource =
        composeSelectionSources(
            GradlePropertySource(target.providers, SUPPORTED_PROPERTY),
            localPropertiesSource(target, SUPPORTED_PROPERTY),
        )

    /**
     * Reads the global `kmptargets.hierarchyTemplate` flag (Gradle property or `local.properties`).
     * `null` means "not set — use the built-in default"; anything other than `true`/`false`
     * (case-insensitive) is treated as unset.
     */
    private fun hierarchyTemplateEnabledGlobally(target: Project): Boolean? =
        composeSelectionSources(
                GradlePropertySource(target.providers, HIERARCHY_TEMPLATE_PROPERTY),
                localPropertiesSource(target, HIERARCHY_TEMPLATE_PROPERTY),
            )
            .read()
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull()

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

    internal companion object {
        const val KMP_TARGETS: String = "KMP_TARGETS"
        const val SUPPORTED_PROPERTY: String = "kmptargets.supported"
        const val HIERARCHY_TEMPLATE_PROPERTY: String = "kmptargets.hierarchyTemplate"
        const val KGP_ID: String = "org.jetbrains.kotlin.multiplatform"
    }
}

/**
 * Entry point convention plugins use to declare a module's supported targets and wire KGP. Lives in
 * the core so the registration mechanism (intersection, idempotency, ordering) has a single home
 * and convention plugins stay trivial.
 */
public object KmpTargets {

    /**
     * Applies the core plugin, declares [supported] for this module, and applies the Kotlin
     * Multiplatform plugin if it isn't already. Fails fast if the user applied
     * `kotlin("multiplatform")` themselves — a convention plugin owns KGP application, and a
     * pre-applied KGP would register targets before the supported set narrows them.
     */
    public fun applyConvention(project: Project, supported: KmpTargetSet) {
        project.pluginManager.apply(KmpTargetsPlugin::class.java)
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        check(!project.plugins.hasPlugin(KmpTargetsPlugin.KGP_ID) || ext.kgpAppliedByConvention) {
            "kmp-targets: do not apply kotlin(\"multiplatform\") yourself when using a kmp-targets " +
                "convention plugin — it applies the Kotlin Multiplatform plugin for you " +
                "(project '${project.path}')."
        }
        declareSupported(project, supported)
        if (!project.plugins.hasPlugin(KmpTargetsPlugin.KGP_ID)) {
            project.pluginManager.apply(KmpTargetsPlugin.KGP_ID)
            ext.kgpAppliedByConvention = true
        }
    }

    /**
     * Adds [add] to this module's supported set and (re)runs an idempotent registration pass once
     * KGP is present. Calling it repeatedly is how `.mobile` + `.web` compose into the union.
     */
    public fun declareSupported(project: Project, add: KmpTargetSet) {
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        ext.accumulateSupported(add)
        project.pluginManager.withPlugin(KmpTargetsPlugin.KGP_ID) { registerResolved(project, ext) }
    }

    /** Registers `selection ∩ supported`, skipping leaves already registered (idempotent). */
    internal fun registerResolved(project: Project, ext: KmpTargetsExtension) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val effective = ext.selection.get() intersect ext.effectiveSupported()
        (effective.members - ext.registered).forEach { leaf ->
            registerTarget(kotlin, leaf)
            ext.registered.add(leaf)
        }
    }

    /**
     * Applies the minimal hierarchy template for [active], at most once per module. Calling
     * `applyHierarchyTemplate` is itself what suppresses KGP's costly default: KGP only
     * auto-applies its default when no template has been applied yet. When disabled (or the active
     * set is empty), we apply nothing and let KGP fall back to its default.
     */
    internal fun maybeApplyHierarchyTemplate(
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
