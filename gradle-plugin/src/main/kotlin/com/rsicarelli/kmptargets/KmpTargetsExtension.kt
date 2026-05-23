package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import org.gradle.api.provider.Property

/**
 * Public DSL surface exposed by the plugin as the `kmpTargets` extension.
 *
 * The plugin separates two facts:
 * - [selection] — what the user wants to build *now*, global, resolved from `KMP_TARGETS` (falling
 *   back to [fallback]). This is the same value for every module in the build.
 * - [supported] — what *this module* can build, declared per-module by the convention plugin id it
 *   applies (e.g. `com.rsicarelli.kmptargets.mobile`) or the `kmptargets.supported` property. When
 *   nothing declares it, [effectiveSupported] defaults to [KmpTargetSet.all].
 *
 * The plugin registers `selection ∩ supported`. `supported` is **not** settable from the
 * build-script body: that runs after KGP target registration has already happened (the "timing
 * wall"), so a DSL setter would silently no-op. Declarations therefore flow through apply-time
 * channels only.
 *
 * All values are immutable `KmpTargetSet`s of `data object` leaves, so the extension is
 * configuration-cache safe — no `Project` or task state is captured.
 */
public abstract class KmpTargetsExtension {

    public abstract val selection: Property<KmpTargetSet>

    public abstract val fallback: Property<KmpTargetSet>

    /**
     * The set of targets this module can build. Unset means "not declared"; [effectiveSupported]
     * treats that as [KmpTargetSet.all]. Written additively via [accumulateSupported] so multiple
     * convention plugins compose (e.g. `.mobile` + `.web`).
     */
    public abstract val supported: Property<KmpTargetSet>

    /** Leaves already registered with KGP, so repeated registration passes are idempotent. */
    internal val registered: MutableSet<KmpTarget> = mutableSetOf()

    /**
     * True once a kmp-targets convention plugin has applied KGP, distinguishing it from a
     * user-applied `kotlin("multiplatform")`.
     */
    internal var kgpAppliedByConvention: Boolean = false

    /** Unions [add] into [supported]. Order-independent, so composition is commutative. */
    internal fun accumulateSupported(add: KmpTargetSet) {
        supported.set((supported.orNull ?: KmpTargetSet.empty) + add)
    }

    /** The resolved supported set: the accumulated declaration, or [KmpTargetSet.all] if none. */
    internal fun effectiveSupported(): KmpTargetSet = supported.orNull ?: KmpTargetSet.all
}
