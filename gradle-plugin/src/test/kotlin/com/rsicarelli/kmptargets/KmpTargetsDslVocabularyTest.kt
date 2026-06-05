package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure, Gradle-free coverage of the [KmpTargetsDsl] receiver vocabulary used inside `kmpTargets {
 * supports { … } }`. Every preset and every leaf must map to exactly the same `KmpTargetSet` the
 * string grammar and the public model produce — otherwise the type-safe DSL would silently disagree
 * with `kmptargets.targets=…` and the docs.
 */
class KmpTargetsDslVocabularyTest {

    private fun leaf(target: KmpTarget) = KmpTargetSet.of(target)

    // --- Presets ---------------------------------------------------------------------------------

    @Test
    fun `given the DSL presets when read then each equals its KmpTargetSet counterpart`() {
        assertEquals(KmpTargetSet.all, KmpTargetsDsl.all)
        assertEquals(KmpTargetSet.empty, KmpTargetsDsl.empty)
        assertEquals(KmpTargetSet.native, KmpTargetsDsl.native)
        assertEquals(KmpTargetSet.apple, KmpTargetsDsl.apple)
        assertEquals(KmpTargetSet.appleMobile, KmpTargetsDsl.appleMobile)
        assertEquals(KmpTargetSet.appleDesktop, KmpTargetsDsl.appleDesktop)
        assertEquals(KmpTargetSet.appleWatch, KmpTargetsDsl.appleWatch)
        assertEquals(KmpTargetSet.appleTv, KmpTargetsDsl.appleTv)
        assertEquals(KmpTargetSet.linux, KmpTargetsDsl.linux)
        assertEquals(KmpTargetSet.mingw, KmpTargetsDsl.mingw)
        assertEquals(KmpTargetSet.androidNative, KmpTargetsDsl.androidNative)
        assertEquals(KmpTargetSet.web, KmpTargetsDsl.web)
        assertEquals(KmpTargetSet.jvmFamily, KmpTargetsDsl.jvmFamily)
        assertEquals(KmpTargetSet.mobile, KmpTargetsDsl.mobile)
    }

    @Test
    fun `given the windows alias when read then it equals the mingw preset`() {
        assertEquals(KmpTargetSet.mingw, KmpTargetsDsl.windows)
    }

    // --- Leaves ----------------------------------------------------------------------------------

    @Test
    fun `given the JVM-family leaves when read then each equals its single-target set`() {
        assertEquals(leaf(KmpTarget.Jvm.Android), KmpTargetsDsl.androidTarget)
        assertEquals(leaf(KmpTarget.Jvm.Desktop), KmpTargetsDsl.jvm)
    }

    @Test
    fun `given the iOS leaves when read then each equals its single-target set`() {
        assertEquals(leaf(KmpTarget.Native.Apple.Ios.Arm64), KmpTargetsDsl.iosArm64)
        assertEquals(
            leaf(KmpTarget.Native.Apple.Ios.SimulatorArm64),
            KmpTargetsDsl.iosSimulatorArm64,
        )
        assertEquals(leaf(KmpTarget.Native.Apple.Ios.X64), KmpTargetsDsl.iosX64)
    }

    @Test
    fun `given the macOS leaves when read then each equals its single-target set`() {
        assertEquals(leaf(KmpTarget.Native.Apple.Macos.Arm64), KmpTargetsDsl.macosArm64)
        assertEquals(leaf(KmpTarget.Native.Apple.Macos.X64), KmpTargetsDsl.macosX64)
    }

    @Test
    fun `given the watchOS leaves when read then each equals its single-target set`() {
        assertEquals(leaf(KmpTarget.Native.Apple.Watchos.Arm64), KmpTargetsDsl.watchosArm64)
        assertEquals(leaf(KmpTarget.Native.Apple.Watchos.Arm32), KmpTargetsDsl.watchosArm32)
        assertEquals(leaf(KmpTarget.Native.Apple.Watchos.X64), KmpTargetsDsl.watchosX64)
        assertEquals(
            leaf(KmpTarget.Native.Apple.Watchos.SimulatorArm64),
            KmpTargetsDsl.watchosSimulatorArm64,
        )
        assertEquals(
            leaf(KmpTarget.Native.Apple.Watchos.DeviceArm64),
            KmpTargetsDsl.watchosDeviceArm64,
        )
    }

    @Test
    fun `given the tvOS leaves when read then each equals its single-target set`() {
        assertEquals(leaf(KmpTarget.Native.Apple.Tvos.Arm64), KmpTargetsDsl.tvosArm64)
        assertEquals(leaf(KmpTarget.Native.Apple.Tvos.X64), KmpTargetsDsl.tvosX64)
        assertEquals(
            leaf(KmpTarget.Native.Apple.Tvos.SimulatorArm64),
            KmpTargetsDsl.tvosSimulatorArm64,
        )
    }

    @Test
    fun `given the Linux and MinGW leaves when read then each equals its single-target set`() {
        assertEquals(leaf(KmpTarget.Native.Linux.X64), KmpTargetsDsl.linuxX64)
        assertEquals(leaf(KmpTarget.Native.Linux.Arm64), KmpTargetsDsl.linuxArm64)
        assertEquals(leaf(KmpTarget.Native.Mingw.X64), KmpTargetsDsl.mingwX64)
    }

    @Test
    fun `given the Android Native leaves when read then each equals its single-target set`() {
        assertEquals(leaf(KmpTarget.Native.AndroidNative.Arm32), KmpTargetsDsl.androidNativeArm32)
        assertEquals(leaf(KmpTarget.Native.AndroidNative.Arm64), KmpTargetsDsl.androidNativeArm64)
        assertEquals(leaf(KmpTarget.Native.AndroidNative.X86), KmpTargetsDsl.androidNativeX86)
        assertEquals(leaf(KmpTarget.Native.AndroidNative.X64), KmpTargetsDsl.androidNativeX64)
    }

    @Test
    fun `given the web leaves when read then each equals its single-target set`() {
        assertEquals(leaf(KmpTarget.Web.Js), KmpTargetsDsl.js)
        assertEquals(leaf(KmpTarget.Web.WasmJs), KmpTargetsDsl.wasmJs)
        assertEquals(leaf(KmpTarget.Web.WasmWasi), KmpTargetsDsl.wasmWasi)
    }

    @Test
    fun `given every shipped leaf when unioned via the DSL vocabulary then it equals KmpTargetSet all`() {
        val everyLeaf =
            KmpTargetsDsl.androidTarget +
                KmpTargetsDsl.jvm +
                KmpTargetsDsl.iosArm64 +
                KmpTargetsDsl.iosSimulatorArm64 +
                KmpTargetsDsl.iosX64 +
                KmpTargetsDsl.macosArm64 +
                KmpTargetsDsl.macosX64 +
                KmpTargetsDsl.watchosArm64 +
                KmpTargetsDsl.watchosArm32 +
                KmpTargetsDsl.watchosX64 +
                KmpTargetsDsl.watchosSimulatorArm64 +
                KmpTargetsDsl.watchosDeviceArm64 +
                KmpTargetsDsl.tvosArm64 +
                KmpTargetsDsl.tvosX64 +
                KmpTargetsDsl.tvosSimulatorArm64 +
                KmpTargetsDsl.linuxX64 +
                KmpTargetsDsl.linuxArm64 +
                KmpTargetsDsl.mingwX64 +
                KmpTargetsDsl.androidNativeArm32 +
                KmpTargetsDsl.androidNativeArm64 +
                KmpTargetsDsl.androidNativeX86 +
                KmpTargetsDsl.androidNativeX64 +
                KmpTargetsDsl.js +
                KmpTargetsDsl.wasmJs +
                KmpTargetsDsl.wasmWasi
        assertEquals(KmpTargetSet.all, everyLeaf)
        assertEquals(25, everyLeaf.size)
    }

    // --- Algebra ---------------------------------------------------------------------------------

    @Test
    fun `given preset union via the DSL when composed then it equals the model union`() {
        assertEquals(
            KmpTargetSet.mobile + KmpTargetSet.web,
            KmpTargetsDsl.mobile + KmpTargetsDsl.web,
        )
    }

    @Test
    fun `given a preset minus a leaf via the DSL when composed then the leaf is removed`() {
        val expected = KmpTargetSet.appleMobile - KmpTarget.Native.Apple.Ios.X64
        assertEquals(expected, KmpTargetsDsl.appleMobile - KmpTargetsDsl.iosX64)
        assertTrue(
            KmpTarget.Native.Apple.Ios.X64 !in (KmpTargetsDsl.appleMobile - KmpTargetsDsl.iosX64)
        )
    }

    @Test
    fun `given empty plus two leaves via the DSL when composed then only those leaves are present`() {
        val set = KmpTargetsDsl.empty + KmpTargetsDsl.jvm + KmpTargetsDsl.iosArm64
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64), set)
    }
}
