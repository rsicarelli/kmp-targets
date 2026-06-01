package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet

/**
 * Marks the receiver of the type-safe `kmpTargets { supports { … } }` block so the outer
 * [KmpTargetsExtension] members are not accidentally in scope inside a target expression.
 */
@DslMarker public annotation class KmpTargetsDslMarker

/**
 * Vocabulary in scope inside a `supports { … }` block.
 *
 * Every preset and every leaf is exposed as a [KmpTargetSet], so the whole grammar is just set
 * algebra with `+` and `-`:
 * ```kotlin
 * kmpTargets {
 *     supports { mobile + web }              // two presets
 *     supports { apple + jvm - iosX64 }      // presets, leaf subtraction
 *     supports { iosArm64 + iosSimulatorArm64 } // bare leaves
 * }
 * ```
 *
 * Names mirror the canonical KGP target ids ([KmpTarget.id]) and the [KmpTargetSet] presets
 * exactly, so what you read in build files matches the `KMP_TARGETS` string grammar and the docs.
 * Build-logic that needs a raw value can call the [KmpTargetsExtension.supports] overload:
 * `supports(KmpTargetSet.mobile + KmpTargetSet.web)`.
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

    /**
     * Android (the JVM-backed `androidTarget`); registers only when AGP is applied to the module.
     */
    public val androidTarget: KmpTargetSet = leaf(KmpTarget.Jvm.Android)

    /** `jvm` — the JVM desktop target. */
    public val jvm: KmpTargetSet = leaf(KmpTarget.Jvm.Desktop)

    /** iOS on a physical 64-bit device. */
    public val iosArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Ios.Arm64)

    /** iOS Simulator on Apple-silicon hosts. */
    public val iosSimulatorArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Ios.SimulatorArm64)

    /** iOS Simulator on Intel hosts. */
    public val iosX64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Ios.X64)

    /** macOS on Apple-silicon. */
    public val macosArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Macos.Arm64)

    /** macOS on Intel. */
    public val macosX64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Macos.X64)

    /** watchOS on a 64-bit device. */
    public val watchosArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Watchos.Arm64)

    /** watchOS on an older 32-bit device. */
    public val watchosArm32: KmpTargetSet = leaf(KmpTarget.Native.Apple.Watchos.Arm32)

    /** watchOS Simulator on Intel hosts. */
    public val watchosX64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Watchos.X64)

    /** watchOS Simulator on Apple-silicon hosts. */
    public val watchosSimulatorArm64: KmpTargetSet =
        leaf(KmpTarget.Native.Apple.Watchos.SimulatorArm64)

    /** watchOS on a 64-bit device using the modern arm64 ABI. */
    public val watchosDeviceArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Watchos.DeviceArm64)

    /** tvOS on a physical 64-bit device. */
    public val tvosArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Tvos.Arm64)

    /** tvOS Simulator on Intel hosts. */
    public val tvosX64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Tvos.X64)

    /** tvOS Simulator on Apple-silicon hosts. */
    public val tvosSimulatorArm64: KmpTargetSet = leaf(KmpTarget.Native.Apple.Tvos.SimulatorArm64)

    /** Linux on x86-64. */
    public val linuxX64: KmpTargetSet = leaf(KmpTarget.Native.Linux.X64)

    /** Linux on arm64 (e.g. aarch64 servers, Raspberry Pi). */
    public val linuxArm64: KmpTargetSet = leaf(KmpTarget.Native.Linux.Arm64)

    /** Windows on x86-64, via the MinGW toolchain. */
    public val mingwX64: KmpTargetSet = leaf(KmpTarget.Native.Mingw.X64)

    /** Android NDK native on 32-bit ARM. */
    public val androidNativeArm32: KmpTargetSet = leaf(KmpTarget.Native.AndroidNative.Arm32)

    /** Android NDK native on 64-bit ARM. */
    public val androidNativeArm64: KmpTargetSet = leaf(KmpTarget.Native.AndroidNative.Arm64)

    /** Android NDK native on 32-bit x86. */
    public val androidNativeX86: KmpTargetSet = leaf(KmpTarget.Native.AndroidNative.X86)

    /** Android NDK native on x86-64. */
    public val androidNativeX64: KmpTargetSet = leaf(KmpTarget.Native.AndroidNative.X64)

    /** JavaScript, compiled to run on Node.js and browsers. */
    public val js: KmpTargetSet = leaf(KmpTarget.Web.Js)

    /** WebAssembly for the browser/JS host (`wasm-js`). */
    public val wasmJs: KmpTargetSet = leaf(KmpTarget.Web.WasmJs)

    /** WebAssembly for standalone WASI runtimes (`wasm-wasi`). */
    public val wasmWasi: KmpTargetSet = leaf(KmpTarget.Web.WasmWasi)

    private fun leaf(target: KmpTarget): KmpTargetSet = KmpTargetSet.of(target)
}
