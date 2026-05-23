package com.rsicarelli.kmptargets.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class KmpTargetTest {

    @Test
    fun `given KmpTarget Jvm Android when accessing id then returns androidTarget`() {
        assertEquals("androidTarget", KmpTarget.Jvm.Android.id)
    }

    @Test
    fun `given KmpTarget Jvm Desktop when accessing id then returns jvm`() {
        assertEquals("jvm", KmpTarget.Jvm.Desktop.id)
    }

    @Test
    fun `given KmpTarget Native Apple Ios leaves when accessing id then returns canonical KGP name`() {
        assertEquals("iosArm64", KmpTarget.Native.Apple.Ios.Arm64.id)
        assertEquals("iosSimulatorArm64", KmpTarget.Native.Apple.Ios.SimulatorArm64.id)
    }

    @Test
    fun `given KmpTarget Native Apple Macos leaves when accessing id then returns canonical KGP name`() {
        assertEquals("macosArm64", KmpTarget.Native.Apple.Macos.Arm64.id)
        assertEquals("macosX64", KmpTarget.Native.Apple.Macos.X64.id)
    }

    @Test
    fun `given KmpTarget Web leaves when accessing id then returns canonical KGP name`() {
        assertEquals("js", KmpTarget.Web.Js.id)
        assertEquals("wasmJs", KmpTarget.Web.WasmJs.id)
        assertEquals("wasmWasi", KmpTarget.Web.WasmWasi.id)
    }

    @Test
    fun `given KmpTarget Jvm Android when checking aliases then contains user-friendly android`() {
        assertTrue("android" in KmpTarget.Jvm.Android.aliases)
    }

    @Test
    fun `given KmpTarget Jvm Desktop when checking aliases then contains desktop`() {
        assertTrue("desktop" in KmpTarget.Jvm.Desktop.aliases)
    }

    @Test
    fun `given KmpTarget all when enumerated then contains every shipped leaf exactly once`() {
        val expected =
            setOf<KmpTarget>(
                KmpTarget.Jvm.Android,
                KmpTarget.Jvm.Desktop,
                KmpTarget.Native.Apple.Ios.Arm64,
                KmpTarget.Native.Apple.Ios.SimulatorArm64,
                KmpTarget.Native.Apple.Ios.X64,
                KmpTarget.Native.Apple.Macos.Arm64,
                KmpTarget.Native.Apple.Macos.X64,
                KmpTarget.Native.Apple.Watchos.Arm64,
                KmpTarget.Native.Apple.Watchos.Arm32,
                KmpTarget.Native.Apple.Watchos.X64,
                KmpTarget.Native.Apple.Watchos.SimulatorArm64,
                KmpTarget.Native.Apple.Watchos.DeviceArm64,
                KmpTarget.Native.Apple.Tvos.Arm64,
                KmpTarget.Native.Apple.Tvos.X64,
                KmpTarget.Native.Apple.Tvos.SimulatorArm64,
                KmpTarget.Native.Linux.X64,
                KmpTarget.Native.Linux.Arm64,
                KmpTarget.Native.Mingw.X64,
                KmpTarget.Native.AndroidNative.Arm32,
                KmpTarget.Native.AndroidNative.Arm64,
                KmpTarget.Native.AndroidNative.X86,
                KmpTarget.Native.AndroidNative.X64,
                KmpTarget.Web.Js,
                KmpTarget.Web.WasmJs,
                KmpTarget.Web.WasmWasi,
            )
        assertEquals(expected, KmpTarget.all)
    }

    @Test
    fun `given new native leaves when accessing id then returns canonical KGP name`() {
        assertEquals("iosX64", KmpTarget.Native.Apple.Ios.X64.id)
        assertEquals("watchosArm64", KmpTarget.Native.Apple.Watchos.Arm64.id)
        assertEquals("watchosDeviceArm64", KmpTarget.Native.Apple.Watchos.DeviceArm64.id)
        assertEquals("tvosSimulatorArm64", KmpTarget.Native.Apple.Tvos.SimulatorArm64.id)
        assertEquals("linuxArm64", KmpTarget.Native.Linux.Arm64.id)
        assertEquals("mingwX64", KmpTarget.Native.Mingw.X64.id)
        assertEquals("androidNativeArm64", KmpTarget.Native.AndroidNative.Arm64.id)
    }

    @Test
    fun `given watchOS and tvOS leaves when checked then they belong to Apple and Native branches`() {
        val watch: KmpTarget = KmpTarget.Native.Apple.Watchos.Arm64
        assertTrue(watch is KmpTarget.Native.Apple)
        assertTrue(watch is KmpTarget.Native.Apple.Watchos)
        val tv: KmpTarget = KmpTarget.Native.Apple.Tvos.Arm64
        assertTrue(tv is KmpTarget.Native.Apple.Tvos)
    }

    @Test
    fun `given Linux Mingw AndroidNative leaves when checked then they belong to Native but not Apple`() {
        val nonApple =
            listOf<KmpTarget>(
                KmpTarget.Native.Linux.X64,
                KmpTarget.Native.Mingw.X64,
                KmpTarget.Native.AndroidNative.Arm64,
            )
        nonApple.forEach { leaf ->
            assertTrue(leaf is KmpTarget.Native, "$leaf should be Native")
            assertTrue(leaf !is KmpTarget.Native.Apple, "$leaf should not be Apple")
        }
    }

    @Test
    fun `given KmpTarget all when checked for unique ids then no duplicates`() {
        val ids = KmpTarget.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids in registry: $ids")
    }

    @Test
    fun `given KmpTarget all when checking aliases then no alias collides with a different leaf's id`() {
        KmpTarget.all.forEach { target ->
            target.aliases.forEach { alias ->
                val collidingLeaf = KmpTarget.all.firstOrNull { it.id == alias }
                if (collidingLeaf != null) {
                    assertEquals(
                        target,
                        collidingLeaf,
                        "alias '$alias' on ${target.id} collides with leaf id of ${collidingLeaf.id}",
                    )
                }
            }
        }
    }

    @Test
    fun `given Ios leaf when checked then it belongs to Apple and Native branches`() {
        val arm64: KmpTarget = KmpTarget.Native.Apple.Ios.Arm64
        assertTrue(arm64 is KmpTarget.Native)
        assertTrue(arm64 is KmpTarget.Native.Apple)
        assertTrue(arm64 is KmpTarget.Native.Apple.Ios)
    }

    @Test
    fun `given Macos leaf when checked then it belongs to Apple and Native branches`() {
        val macos: KmpTarget = KmpTarget.Native.Apple.Macos.Arm64
        assertTrue(macos is KmpTarget.Native)
        assertTrue(macos is KmpTarget.Native.Apple)
        assertTrue(macos is KmpTarget.Native.Apple.Macos)
    }

    @Test
    fun `given Js leaf when checked then it belongs to Web branch`() {
        val js: KmpTarget = KmpTarget.Web.Js
        assertTrue(js is KmpTarget.Web)
    }

    @Test
    fun `given KmpTarget leaves when used in exhaustive when over Web then compiler accepts without else`() {
        val target: KmpTarget.Web = KmpTarget.Web.WasmJs
        val name: String =
            when (target) {
                KmpTarget.Web.Js -> "js"
                KmpTarget.Web.WasmJs -> "wasmJs"
                KmpTarget.Web.WasmWasi -> "wasmWasi"
            }
        assertEquals("wasmJs", name)
    }
}
