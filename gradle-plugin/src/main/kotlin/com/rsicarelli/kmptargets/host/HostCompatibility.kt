package com.rsicarelli.kmptargets.host

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.warnOrFail
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

/**
 * The [KonanTarget] a native [target] compiles to, or `null` for the host-agnostic JVM/Web leaves.
 *
 * Exhaustive over every leaf (mirroring `registerTarget`), so adding a new target forces a
 * compile-time decision here about its host story.
 */
internal fun konanTargetOf(target: KmpTarget): KonanTarget? =
    when (target) {
        KmpTarget.Native.Apple.Ios.Arm64 -> KonanTarget.IOS_ARM64
        KmpTarget.Native.Apple.Ios.SimulatorArm64 -> KonanTarget.IOS_SIMULATOR_ARM64
        KmpTarget.Native.Apple.Ios.X64 -> KonanTarget.IOS_X64
        KmpTarget.Native.Apple.Macos.Arm64 -> KonanTarget.MACOS_ARM64
        KmpTarget.Native.Apple.Macos.X64 -> KonanTarget.MACOS_X64
        KmpTarget.Native.Apple.Watchos.Arm64 -> KonanTarget.WATCHOS_ARM64
        KmpTarget.Native.Apple.Watchos.Arm32 -> KonanTarget.WATCHOS_ARM32
        KmpTarget.Native.Apple.Watchos.X64 -> KonanTarget.WATCHOS_X64
        KmpTarget.Native.Apple.Watchos.SimulatorArm64 -> KonanTarget.WATCHOS_SIMULATOR_ARM64
        KmpTarget.Native.Apple.Watchos.DeviceArm64 -> KonanTarget.WATCHOS_DEVICE_ARM64
        KmpTarget.Native.Apple.Tvos.Arm64 -> KonanTarget.TVOS_ARM64
        KmpTarget.Native.Apple.Tvos.X64 -> KonanTarget.TVOS_X64
        KmpTarget.Native.Apple.Tvos.SimulatorArm64 -> KonanTarget.TVOS_SIMULATOR_ARM64
        KmpTarget.Native.Linux.X64 -> KonanTarget.LINUX_X64
        KmpTarget.Native.Linux.Arm64 -> KonanTarget.LINUX_ARM64
        KmpTarget.Native.Mingw.X64 -> KonanTarget.MINGW_X64
        KmpTarget.Native.AndroidNative.Arm32 -> KonanTarget.ANDROID_ARM32
        KmpTarget.Native.AndroidNative.Arm64 -> KonanTarget.ANDROID_ARM64
        KmpTarget.Native.AndroidNative.X86 -> KonanTarget.ANDROID_X86
        KmpTarget.Native.AndroidNative.X64 -> KonanTarget.ANDROID_X64
        KmpTarget.Jvm.Android,
        KmpTarget.Jvm.Desktop,
        KmpTarget.Web.Js,
        KmpTarget.Web.WasmJs,
        KmpTarget.Web.WasmWasi -> null
    }

/**
 * The subset of [active] whose [KonanTarget] is missing from [enabled] — the host's compilable
 * targets. Host-agnostic leaves (JVM/Web) are never impossible; an empty [active] is vacuously
 * possible everywhere. Callers inject [enabled], so the decision is testable against any simulated
 * host. Strict mode (#34) reuses this as its failure predicate.
 */
internal fun impossibleOnHost(active: KmpTargetSet, enabled: Set<KonanTarget>): KmpTargetSet =
    active
        .filter { leaf -> konanTargetOf(leaf)?.let { it !in enabled } ?: false }
        .fold(KmpTargetSet.empty, KmpTargetSet::plus)

/**
 * Single aggregated advisory naming the [impossible] target ids (sorted) and the current [host].
 * The targets stay registered — selection is host-independent by design — so the message says
 * exactly that. Strict mode (#34) reuses this as its failure-message base.
 */
internal fun hostImpossibleWarning(
    path: String,
    host: KonanTarget,
    impossible: KmpTargetSet,
): String =
    "kmp-targets: '$path' selects ${impossible.members.map { it.id }.sorted()} which cannot be " +
        "compiled on this host (${host.name.uppercase()}) — still registered (selection is " +
        "host-independent), but compile/link tasks for them will fail or be skipped here."

/**
 * The host-advisory step of registration, behind an injected [enabled] set so strict-mode tests can
 * simulate any host (on a macOS machine nothing is host-impossible, so the real path can never be
 * triggered deterministically in-process).
 *
 * Computes the freshly-flagged leaves — [impossibleOnHost] minus [alreadyWarned] — and escalates
 * them through [warnOrFail]: advisory when [strict] is off, `GradleException` with the identical
 * [hostImpossibleWarning] text when on. Returns the fresh set for the caller's dedup bookkeeping
 * (an empty fresh set is a silent no-op, so a leaf is flagged at most once per build). Policy stays
 * owned by [impossibleOnHost]/[hostImpossibleWarning]; this only sequences decision → severity.
 */
internal fun enforceHostCompatibility(
    path: String,
    active: KmpTargetSet,
    enabled: Set<KonanTarget>,
    host: KonanTarget,
    alreadyWarned: Set<KmpTarget>,
    strict: Boolean,
    warn: (String) -> Unit,
): Set<KmpTarget> {
    val fresh = impossibleOnHost(active, enabled).members - alreadyWarned
    if (fresh.isEmpty()) return emptySet()
    warnOrFail(
        strict,
        hostImpossibleWarning(path, host, KmpTargetSet.of(*fresh.toTypedArray())),
        warn,
    )
    return fresh
}

/**
 * Sorted ids of the [active] leaves the simulated host (its [enabled] set) cannot compile — the
 * `kmpTargetsInfo` per-leaf annotation source (#44). Same [impossibleOnHost] decision as the
 * advisory, behind the same injected-enabled-set seam so tests never depend on the running machine.
 */
internal fun hostImpossibleIds(active: KmpTargetSet, enabled: Set<KonanTarget>): List<String> =
    impossibleOnHost(active, enabled).members.map { it.id }.sorted()

/**
 * The running host's enabled targets and display label, touched ONLY behind a KGP-presence check:
 * `HostManager` lives in these function bodies so its classes never load when KGP is absent (the
 * same lazy-resolution pattern eager registration relies on).
 */
internal fun currentHostEnabled(): Set<KonanTarget> = HostManager().enabled.toSet()

internal fun currentHostLabel(): String = HostManager.host.name.uppercase()
