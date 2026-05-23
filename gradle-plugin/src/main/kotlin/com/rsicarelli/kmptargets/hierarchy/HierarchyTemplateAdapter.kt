package com.rsicarelli.kmptargets.hierarchy

import com.rsicarelli.kmptargets.model.KmpTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyBuilder

/**
 * Turns a pure [HierarchySpec] into the builder lambda KGP's `applyHierarchyTemplate { … }`
 * expects.
 *
 * Only the group structure (and the leaves *inside* groups) is emitted: a leaf that sits directly
 * under `common` is skipped, because KGP already attaches every registered target's `*Main` to
 * `commonMain` — emitting `withJvm()` at the common root would be a redundant no-op. So a spec with
 * no groups (e.g. jvm-only, web-only) yields an effectively empty template.
 */
internal fun HierarchySpec.toTemplate(): KotlinHierarchyBuilder.Root.() -> Unit = {
    common { roots.filterIsInstance<HierarchyNode.Group>().forEach { emit(it) } }
}

private fun KotlinHierarchyBuilder.emit(node: HierarchyNode) {
    when (node) {
        is HierarchyNode.Group -> group(node.group.groupName) { node.children.forEach { emit(it) } }
        is HierarchyNode.Leaf -> withLeaf(node.target)
    }
}

/**
 * Attaches a single target to the current group. Exhaustive `when` over the sealed [KmpTarget] —
 * mirrors `KmpTargets.registerTarget`, so adding a new leaf forces a corresponding builder call
 * here (compile error until handled).
 */
private fun KotlinHierarchyBuilder.withLeaf(target: KmpTarget) {
    when (target) {
        KmpTarget.Jvm.Android -> withAndroidTarget()
        KmpTarget.Jvm.Desktop -> withJvm()
        KmpTarget.Native.Apple.Ios.Arm64 -> withIosArm64()
        KmpTarget.Native.Apple.Ios.SimulatorArm64 -> withIosSimulatorArm64()
        KmpTarget.Native.Apple.Ios.X64 -> withIosX64()
        KmpTarget.Native.Apple.Macos.Arm64 -> withMacosArm64()
        KmpTarget.Native.Apple.Macos.X64 -> withMacosX64()
        KmpTarget.Native.Apple.Watchos.Arm64 -> withWatchosArm64()
        KmpTarget.Native.Apple.Watchos.Arm32 -> withWatchosArm32()
        KmpTarget.Native.Apple.Watchos.X64 -> withWatchosX64()
        KmpTarget.Native.Apple.Watchos.SimulatorArm64 -> withWatchosSimulatorArm64()
        KmpTarget.Native.Apple.Watchos.DeviceArm64 -> withWatchosDeviceArm64()
        KmpTarget.Native.Apple.Tvos.Arm64 -> withTvosArm64()
        KmpTarget.Native.Apple.Tvos.X64 -> withTvosX64()
        KmpTarget.Native.Apple.Tvos.SimulatorArm64 -> withTvosSimulatorArm64()
        KmpTarget.Native.Linux.X64 -> withLinuxX64()
        KmpTarget.Native.Linux.Arm64 -> withLinuxArm64()
        KmpTarget.Native.Mingw.X64 -> withMingwX64()
        KmpTarget.Native.AndroidNative.Arm32 -> withAndroidNativeArm32()
        KmpTarget.Native.AndroidNative.Arm64 -> withAndroidNativeArm64()
        KmpTarget.Native.AndroidNative.X86 -> withAndroidNativeX86()
        KmpTarget.Native.AndroidNative.X64 -> withAndroidNativeX64()
        KmpTarget.Web.Js -> withJs()
        KmpTarget.Web.WasmJs -> withWasmJs()
        KmpTarget.Web.WasmWasi -> withWasmWasi()
    }
}
