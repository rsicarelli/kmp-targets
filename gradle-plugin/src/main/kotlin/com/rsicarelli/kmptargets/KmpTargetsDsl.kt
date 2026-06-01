package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet

/**
 * Marks the receiver of the type-safe `kmpTargets { supported { … } }` / `selection { … }` blocks
 * so the outer [KmpTargetsExtension] members are not accidentally in scope inside a target
 * expression.
 */
@DslMarker public annotation class KmpTargetsDslMarker

/**
 * Vocabulary in scope inside a `supported { … }` / `selection { … }` block.
 *
 * Every preset and every leaf is exposed as a [KmpTargetSet], so the whole grammar is just set
 * algebra with `+` and `-`:
 * ```kotlin
 * kmpTargets {
 *     supported { mobile + web }              // two presets
 *     supported { apple + jvm - iosX64 }      // presets, leaf subtraction
 *     supported { iosArm64 + iosSimulatorArm64 } // bare leaves
 * }
 * ```
 *
 * Names mirror the canonical KGP target ids ([KmpTarget.id]) and the [KmpTargetSet] presets
 * exactly, so what you read in build files matches the `KMP_TARGETS` string grammar and the docs.
 * Power users and build-logic convention plugins can bypass this sugar entirely and assign the raw
 * property: `supported.set(KmpTargetSet.mobile + KmpTargetSet.web)`.
 */
@KmpTargetsDslMarker
public object KmpTargetsDsl {

    // --- Presets -------------------------------------------------------------------------------

    /** Every shipped target. */
    public val all: KmpTargetSet = KmpTargetSet.all

    /** No targets — useful as an explicit starting point, e.g. `empty + jvm`. */
    public val empty: KmpTargetSet = KmpTargetSet.empty

    /** Every Kotlin/Native target: Apple + Linux + MinGW + Android Native. */
    public val native: KmpTargetSet = KmpTargetSet.native

    /** All Apple platforms: iOS + macOS + watchOS + tvOS. */
    public val apple: KmpTargetSet = KmpTargetSet.apple

    /** All iOS leaves. */
    public val appleMobile: KmpTargetSet = KmpTargetSet.appleMobile

    /** All macOS leaves. */
    public val appleDesktop: KmpTargetSet = KmpTargetSet.appleDesktop

    /** All watchOS leaves. */
    public val appleWatch: KmpTargetSet = KmpTargetSet.appleWatch

    /** All tvOS leaves. */
    public val appleTv: KmpTargetSet = KmpTargetSet.appleTv

    /** `linuxX64`, `linuxArm64`. */
    public val linux: KmpTargetSet = KmpTargetSet.linux

    /** `mingwX64`. */
    public val mingw: KmpTargetSet = KmpTargetSet.mingw

    /** Alias of [mingw]. */
    public val windows: KmpTargetSet = KmpTargetSet.mingw

    /** The four `androidNative*` targets. */
    public val androidNative: KmpTargetSet = KmpTargetSet.androidNative

    /** `js`, `wasmJs`, `wasmWasi`. */
    public val web: KmpTargetSet = KmpTargetSet.web

    /** `androidTarget`, `jvm`. */
    public val jvmFamily: KmpTargetSet = KmpTargetSet.jvmFamily

    /** `androidTarget` + all iOS. */
    public val mobile: KmpTargetSet = KmpTargetSet.mobile

    // --- Leaves --------------------------------------------------------------------------------

    public val androidTarget: KmpTargetSet = leaf(KmpTarget.Jvm.Android)

    /** `jvm` — the JVM desktop target. */
    public val jvm: KmpTargetSet = leaf(KmpTarget.Jvm.Desktop)

    public val iosArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Ios.Arm64)
    public val iosSimulatorArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Ios.SimulatorArm64)
    public val iosX64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Ios.X64)

    public val macosArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Macos.Arm64)
    public val macosX64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Macos.X64)

    public val watchosArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Watchos.Arm64)
    public val watchosArm32: KmpTargetSet = leaf(KmpTarget.Native.Apple.Watchos.Arm32)
    public val watchosX64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Watchos.X64)
    public val watchosSimulatorArm64: KmpTargetSet =
        leaf(KmpTarget.Native.Apple.Watchos.SimulatorArm64)
    public val watchosDeviceArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Watchos.DeviceArm64)

    public val tvosArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Tvos.Arm64)
    public val tvosX64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Tvos.X64)
    public val tvosSimulatorArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Tvos.SimulatorArm64)

    public val linuxX64: KmpTargetSet = leaf(KmpTarget.Native.Linux.X64)
    public val linuxArm64: KmpTargetSet = leaf(KmpTarget.Native.Linux.Arm64)

    public val mingwX64: KmpTargetSet = leaf(KmpTarget.Native.Mingw.X64)

    public val androidNativeArm32: KmpTargetSet = leaf(KmpTarget.Native.AndroidNative.Arm32)
    public val androidNativeArm64: KmpTargetSet = leaf(KmpTarget.Native.AndroidNative.Arm64)
    public val androidNativeX86: KmpTargetSet = leaf(KmpTarget.Native.AndroidNative.X86)
    public val androidNativeX64: KmpTargetSet = leaf(KmpTarget.Native.AndroidNative.X64)

    public val js: KmpTargetSet = leaf(KmpTarget.Web.Js)
    public val wasmJs: KmpTargetSet = leaf(KmpTarget.Web.WasmJs)
    public val wasmWasi: KmpTargetSet = leaf(KmpTarget.Web.WasmWasi)

    private fun leaf(target: KmpTarget): KmpTargetSet = KmpTargetSet.of(target)
}
