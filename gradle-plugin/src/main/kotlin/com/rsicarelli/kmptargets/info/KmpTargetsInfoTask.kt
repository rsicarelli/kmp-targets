package com.rsicarelli.kmptargets.info

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Prints what the plugin decided for this project — the resolved selection (and which config layer
 * it came from), the declared supported set, the registered intersection, and the vocabulary the
 * parser accepts. Pure report: it never registers targets or mutates any state.
 *
 * Every property is a plain `String`/`Boolean` primitive, wired by the plugin from providers that
 * realize *after* the build-script body (so all `supports { … }` unions are visible) and serialize
 * cleanly into the configuration cache. The action reads only these properties — no `Project`,
 * extension, or model type is ever captured (CLAUDE.md configuration-cache rule).
 *
 * The properties are [Internal] rather than `@Input` on purpose: the task declares no outputs, so
 * it always runs — input fingerprinting would buy nothing for a console report.
 */
@DisableCachingByDefault(because = "console report with no outputs; it must always run")
public abstract class KmpTargetsInfoTask : DefaultTask() {

    /** The path of the project being reported, e.g. `:shared-core`. */
    @get:Internal public abstract val projectPath: Property<String>

    /** Sorted leaf ids of the resolved selection (`resolvedSelection()`). */
    @get:Internal public abstract val selectionIds: ListProperty<String>

    /** Sorted leaf ids of the resolved supported set (`resolvedSupported()`). */
    @get:Internal public abstract val supportedIds: ListProperty<String>

    /** Whether this module ever called `supports { … }` (undeclared registers nothing). */
    @get:Internal public abstract val supportsDeclared: Property<Boolean>

    /** Sorted leaf ids of `selection ∩ supported` — what actually builds for this module. */
    @get:Internal public abstract val registeredIds: ListProperty<String>

    /** Human-readable label of the config layer the selection came from (see [OriginLabels]). */
    @get:Internal public abstract val originLabel: Property<String>

    /** Preset names the parser accepts, in curated declaration order. */
    @get:Internal public abstract val presetNames: ListProperty<String>

    /** Sorted canonical leaf ids the parser accepts. */
    @get:Internal public abstract val leafIds: ListProperty<String>

    /**
     * Sorted ids of registered leaves the current host cannot compile, annotated per leaf in the
     * registered section. Empty when everything compiles here — or when KGP is absent, in which
     * case there is no host data to report and the annotation simply vanishes.
     */
    @get:Internal public abstract val hostImpossibleIds: ListProperty<String>

    /** The current host's display name (e.g. `MACOS_ARM64`); empty when KGP is absent. */
    @get:Internal public abstract val hostLabel: Property<String>

    /** Sorted ids of leaves Kotlin marks deprecated, annotated in the vocabulary listing. */
    @get:Internal public abstract val deprecatedIds: ListProperty<String>

    /**
     * Custom Gradle name the jvm leaf registers under (issue #49), annotated next to `jvm` in the
     * registered section. Unset/blank means the default name — no annotation.
     */
    @get:Internal public abstract val jvmRegisteredAs: Property<String>

    /**
     * True when `androidTarget` is in the registered intersection but no Android Gradle plugin is
     * applied, so registration skipped it (#51). The registered line reports `selection ∩
     * supported` (like the host annotation), so the leaf stays listed and is marked `(skipped: no
     * Android plugin applied)`. False when AGP is present — or when KGP is absent, in which case
     * nothing registers and there is no skip to report.
     */
    @get:Internal public abstract val androidWithoutAgp: Property<Boolean>

    @TaskAction
    public fun report() {
        println(
            formatKmpTargetsInfo(
                projectPath = projectPath.get(),
                selectionIds = selectionIds.get(),
                supportedIds = supportedIds.get(),
                supportsDeclared = supportsDeclared.get(),
                registeredIds = registeredIds.get(),
                originLabel = originLabel.get(),
                presetNames = presetNames.get(),
                leafIds = leafIds.get(),
                hostImpossibleIds = hostImpossibleIds.get(),
                hostLabel = hostLabel.get(),
                deprecatedIds = deprecatedIds.get(),
                jvmRegisteredAs = jvmRegisteredAs.orNull?.takeIf { it.isNotBlank() },
                androidWithoutAgp = androidWithoutAgp.get(),
            )
        )
    }
}
