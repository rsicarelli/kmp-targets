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
 * - **supported** — what *this module* can build, declared per-module by the type-safe [supports]
 *   block (or its raw value overload). It is **explicit**: a module that never calls [supports]
 *   registers no targets at all (just like plain KGP, where every target is declared by hand).
 *   [resolvedSupported] returns [KmpTargetSet.empty] until something declares it.
 * - **selection** — what the user wants to build *now*. It is **global**: resolved from
 *   `kmptargets.targets` (`-P`, env, the dedicated `kmp-targets(.local).properties` files, root
 *   `gradle.properties`, or `local.properties`) and the same value for every module. When no global
 *   is provided, it falls back to [defaultSelection] (itself defaulting to [KmpTargetSet.all]).
 *
 * The plugin registers `selection ∩ supported`. Registration is **eager**: calling [supports] from
 * the build-script body registers the matching targets immediately (no deferred/after-evaluate
 * pass), so anything that reads `kotlin.targets` afterwards sees them. The extension only ever
 * holds immutable `KmpTargetSet`s of `data object` leaves, so it is configuration-cache safe — no
 * `Project` or task state is captured.
 */
public abstract class KmpTargetsExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * The selection used when no global `kmptargets.targets` is provided. Defaults to
     * [KmpTargetSet.all]. Public so build-logic can set a project-wide default lazily; a global
     * `kmptargets.targets` always wins over it. Set it **before** [supports], since [supports]
     * registers eagerly off the resolved selection.
     */
    public abstract val defaultSelection: Property<KmpTargetSet>

    /**
     * Per-project opt-out of the minimal custom hierarchy template. Unset defers to the global
     * `kmptargets.hierarchyTemplate` Gradle property, which itself defaults to `true`. When
     * `false`, the plugin applies no template and KGP falls back to its own
     * `applyDefaultHierarchyTemplate()` — also the escape hatch for a module that supplies its own
     * `applyHierarchyTemplate { … }`.
     *
     * Set it **before** [supports]: the template is applied as part of the eager registration that
     * [supports] triggers, so it must already hold its value by then.
     */
    public abstract val hierarchyTemplate: Property<Boolean>

    /**
     * What this module can build. Unset means "not declared" → no targets register, and
     * [resolvedSupported] returns [KmpTargetSet.empty]. Written by the [supports] block (or its raw
     * overload), which unions on each call.
     */
    internal val supportsProperty: Property<KmpTargetSet> =
        objects.property(KmpTargetSet::class.java)

    /**
     * Fired by [supports]; the plugin wires this to eager registration. Null until the plugin runs.
     */
    internal var onSupports: (() -> Unit)? = null

    /**
     * Declares this module's supported set with the type-safe target vocabulary and **registers**
     * `selection ∩ supported` immediately:
     * ```kotlin
     * kmpTargets { supports { mobile + web - iosX64 } }
     * ```
     *
     * Must be called before anything reads `kotlin.targets` (registration is eager). Calling more
     * than once **unions** the sets — KGP target registration is one-way, so an already-registered
     * target cannot be retracted. To narrow, write the final set inside a single block.
     */
    public fun supports(block: KmpTargetsDsl.() -> KmpTargetSet) {
        supports(KmpTargetsDsl.block())
    }

    /** Raw overload for build-logic: `supports(KmpTargetSet.mobile + KmpTargetSet.web)`. */
    public fun supports(value: KmpTargetSet) {
        supportsProperty.set((supportsProperty.orNull ?: KmpTargetSet.empty) + value)
        onSupports?.invoke()
    }

    /**
     * The global selection resolved from `kmptargets.targets` at apply time, or `null` when no
     * global source provided one. When present it wins over [defaultSelection] (and an explicit
     * empty set is honored — see issue #9), which is what keeps the global switch authoritative.
     * Stored as an immutable [KmpTargetSet], so it stays configuration-cache safe.
     */
    internal var globalSelection: KmpTargetSet? = null

    /**
     * The selection actually used to register targets: the global `kmptargets.targets` if present,
     * otherwise [defaultSelection] (which itself defaults to [KmpTargetSet.all]). Public so
     * build-logic can read the resolved set (e.g. for conditional configuration).
     */
    public fun resolvedSelection(): KmpTargetSet = globalSelection ?: defaultSelection.get()

    /** Leaves already registered with KGP, so repeated registration stays idempotent. */
    internal val registered: MutableSet<KmpTarget> = mutableSetOf()

    /** Leaves already named in a host-impossible warning, so each is warned at most once. */
    internal val hostWarned: MutableSet<KmpTarget> = mutableSetOf()

    /** True once the minimal hierarchy template has been applied, so it is applied at most once. */
    internal var hierarchyTemplateApplied: Boolean = false

    /**
     * The resolved supported set: the declared value (unioned across [supports] calls), or
     * [KmpTargetSet.empty] if none was declared. Public so build-logic (and consumers) can read
     * what this module ended up declaring.
     */
    public fun resolvedSupported(): KmpTargetSet = supportsProperty.orNull ?: KmpTargetSet.empty
}
