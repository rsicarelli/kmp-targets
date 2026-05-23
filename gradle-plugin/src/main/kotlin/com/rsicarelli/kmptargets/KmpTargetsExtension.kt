package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import org.gradle.api.provider.Property

/**
 * Public DSL surface exposed by the plugin as the `kmpTargets` extension.
 *
 * [selection] is the resolved target set the plugin will register with KGP. The plugin sets a
 * convention on it (parsed from the `KMP_TARGETS` property, falling back to [fallback]), and the
 * user can override it explicitly via [selection.set], or via the [only] / [exclude] helpers for
 * common cases.
 *
 * [fallback] is consulted only when `KMP_TARGETS` is absent. Convention: [KmpTargetSet.all].
 *
 * Both properties hold immutable `KmpTargetSet` values composed of `data object` leaves, so the
 * extension itself is configuration-cache safe — no `Project` or mutable state is captured.
 */
public abstract class KmpTargetsExtension {

    public abstract val selection: Property<KmpTargetSet>

    public abstract val fallback: Property<KmpTargetSet>

    /** Replaces the current selection with exactly [targets]. Last write wins. */
    public fun only(vararg targets: KmpTarget) {
        selection.set(KmpTargetSet.of(*targets))
    }

    /**
     * Removes [targets] from the current selection. Resolves the current value eagerly against
     * [selection] (which may carry a convention from the plugin) and falls back to [fallback] or
     * [KmpTargetSet.empty] if neither is set.
     */
    public fun exclude(vararg targets: KmpTarget) {
        val excluded = KmpTargetSet.of(*targets)
        val current = selection.orNull ?: fallback.orNull ?: KmpTargetSet.empty
        selection.set(current - excluded)
    }
}
