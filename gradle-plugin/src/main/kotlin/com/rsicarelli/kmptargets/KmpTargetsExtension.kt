package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

/**
 * Public DSL surface exposed by the plugin as the `kmpTargets` extension.
 *
 * The plugin separates two facts:
 * - **selection** — what the user wants to build *now*. Normally global, resolved from
 *   `KMP_TARGETS` (falling back to [fallback]), and the same value for every module. A per-module
 *   [selection] block sets the module's *default* desired set; a global `KMP_TARGETS` (`-P`, env,
 *   root `gradle.properties`, or `local.properties`) always overrides it, so CI stays
 *   authoritative.
 * - **supported** — what *this module* can build, declared per-module by the type-safe [supported]
 *   block (or its raw value overload). When nothing declares it, [effectiveSupported] defaults to
 *   [KmpTargetSet.all].
 *
 * The plugin registers `selection ∩ supported`. Both [supported] and [selection] are written from
 * the build-script body via the type-safe blocks below: a single deferred (after-evaluate)
 * registration pass picks up whatever the body set. The extension only ever holds immutable
 * `KmpTargetSet`s of `data object` leaves, so it is configuration-cache safe — no `Project` or task
 * state is captured.
 */
public abstract class KmpTargetsExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * The default selection used when no global `KMP_TARGETS` is provided. Defaults to
     * [KmpTargetSet.all]. Public so build-logic can set a project-wide floor lazily.
     */
    public abstract val fallback: Property<KmpTargetSet>

    /**
     * Per-project opt-out of the minimal custom hierarchy template. Unset defers to the global
     * `kmptargets.hierarchyTemplate` Gradle property, which itself defaults to `true`. When
     * `false`, the plugin applies no template and KGP falls back to its own
     * `applyDefaultHierarchyTemplate()` — also the escape hatch for a module that supplies its own
     * `applyHierarchyTemplate { … }`.
     *
     * Readable from the build-script body: the template is applied in a deferred pass (after
     * evaluation), by which point the DSL value has been set.
     */
    public abstract val hierarchyTemplate: Property<Boolean>

    /**
     * What this module can build. Unset means "not declared"; [effectiveSupported] treats that as
     * [KmpTargetSet.all]. Written by the [supported] block (or its raw overload).
     */
    internal val supportedProperty: Property<KmpTargetSet> =
        objects.property(KmpTargetSet::class.java)

    /**
     * The per-module *default* desired selection. Its convention is [fallback]; a global
     * `KMP_TARGETS` ([globalSelection]) overrides it in [resolvedSelection].
     */
    internal val selectionProperty: Property<KmpTargetSet> =
        objects.property(KmpTargetSet::class.java)

    /**
     * Declares this module's supported set with the type-safe target vocabulary:
     * ```kotlin
     * kmpTargets { supported { mobile + web - iosX64 } }
     * ```
     *
     * Assigns the supported set; calling more than once replaces. If you want union semantics,
     * write the union inside the block (`supported { mobile + web }`).
     */
    public fun supported(block: KmpTargetsDsl.() -> KmpTargetSet) {
        supportedProperty.set(KmpTargetsDsl.block())
    }

    /** Raw overload for build-logic: `supported(KmpTargetSet.mobile + KmpTargetSet.web)`. */
    public fun supported(value: KmpTargetSet) {
        supportedProperty.set(value)
    }

    /**
     * Declares this module's *default* desired selection with the type-safe target vocabulary:
     * ```kotlin
     * kmpTargets { selection { jvm + iosArm64 } }
     * ```
     *
     * A global `KMP_TARGETS` (CLI `-P`, environment variable, root `gradle.properties`, or
     * `local.properties`) always overrides this, preserving the "one global switch wins" guarantee.
     */
    public fun selection(block: KmpTargetsDsl.() -> KmpTargetSet) {
        selectionProperty.set(KmpTargetsDsl.block())
    }

    /** Raw overload for build-logic: `selection(KmpTargetSet.jvmFamily)`. */
    public fun selection(value: KmpTargetSet) {
        selectionProperty.set(value)
    }

    /**
     * The global selection resolved from `KMP_TARGETS` at apply time, or `null` when no global
     * source provided one. When present it wins over the per-module [selection] block (and an
     * explicit empty set is honored — see issue #9), which is what keeps the global switch
     * authoritative. Stored as an immutable [KmpTargetSet], so capturing it in deferred closures
     * stays configuration-cache safe.
     */
    internal var globalSelection: KmpTargetSet? = null

    /**
     * The selection actually used to register targets: the global override if present, otherwise
     * the per-module selection (whose own convention is [fallback], defaulting to
     * [KmpTargetSet.all]). Public so build-logic can read the resolved set (e.g. for conditional
     * configuration).
     */
    public fun resolvedSelection(): KmpTargetSet = globalSelection ?: selectionProperty.get()

    /** Leaves already registered with KGP, so repeated registration passes are idempotent. */
    internal val registered: MutableSet<KmpTarget> = mutableSetOf()

    /** True once the minimal hierarchy template has been applied, so it is applied at most once. */
    internal var hierarchyTemplateApplied: Boolean = false

    /**
     * The resolved supported set: the declared value, or [KmpTargetSet.all] if none. Public so
     * build-logic (and consumers) can read what this module ended up declaring.
     */
    public fun effectiveSupported(): KmpTargetSet = supportedProperty.orNull ?: KmpTargetSet.all
}
