package com.rsicarelli.kmptargets.hierarchy

import com.rsicarelli.kmptargets.model.KmpTarget

/**
 * One contributor per [HierarchyGroup]: it knows the [group] it represents and which [KmpTarget]
 * leaves belong to it ([owns]). Membership is expressed as a pure predicate over the sealed
 * [KmpTarget] branches — the single source of truth, so it never drifts when a leaf is added.
 *
 * The composer walks [all] to discover, for any active target, its most-specific owning group, then
 * prunes and collapses redundant groups. Keeping membership here (not as duplicated leaf lists)
 * means a new target is classified automatically by its branch type.
 */
internal sealed interface HierarchyContributor {

    val group: HierarchyGroup

    fun owns(target: KmpTarget): Boolean

    companion object {
        /** All contributors, deepest-first is not required; ordering is by taxonomy for clarity. */
        val all: List<HierarchyContributor> =
            listOf(
                NativeContributor,
                AppleContributor,
                IosContributor,
                MacosContributor,
                WatchosContributor,
                TvosContributor,
                LinuxContributor,
                MingwContributor,
                AndroidNativeContributor,
            )
    }
}

internal data object NativeContributor : HierarchyContributor {
    override val group = HierarchyGroup.NATIVE

    override fun owns(target: KmpTarget): Boolean = target is KmpTarget.Native
}

internal data object AppleContributor : HierarchyContributor {
    override val group = HierarchyGroup.APPLE

    override fun owns(target: KmpTarget): Boolean = target is KmpTarget.Native.Apple
}

internal data object IosContributor : HierarchyContributor {
    override val group = HierarchyGroup.IOS

    override fun owns(target: KmpTarget): Boolean = target is KmpTarget.Native.Apple.Ios
}

internal data object MacosContributor : HierarchyContributor {
    override val group = HierarchyGroup.MACOS

    override fun owns(target: KmpTarget): Boolean = target is KmpTarget.Native.Apple.Macos
}

internal data object WatchosContributor : HierarchyContributor {
    override val group = HierarchyGroup.WATCHOS

    override fun owns(target: KmpTarget): Boolean = target is KmpTarget.Native.Apple.Watchos
}

internal data object TvosContributor : HierarchyContributor {
    override val group = HierarchyGroup.TVOS

    override fun owns(target: KmpTarget): Boolean = target is KmpTarget.Native.Apple.Tvos
}

internal data object LinuxContributor : HierarchyContributor {
    override val group = HierarchyGroup.LINUX

    override fun owns(target: KmpTarget): Boolean = target is KmpTarget.Native.Linux
}

internal data object MingwContributor : HierarchyContributor {
    override val group = HierarchyGroup.MINGW

    override fun owns(target: KmpTarget): Boolean = target is KmpTarget.Native.Mingw
}

internal data object AndroidNativeContributor : HierarchyContributor {
    override val group = HierarchyGroup.ANDROID_NATIVE

    override fun owns(target: KmpTarget): Boolean = target is KmpTarget.Native.AndroidNative
}
