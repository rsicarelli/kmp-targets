package com.rsicarelli.kmptargets.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class KmpTargetSetTest {

    @Test
    fun `given empty preset when accessed then has zero members`() {
        assertEquals(0, KmpTargetSet.empty.size)
        assertTrue(KmpTargetSet.empty.isEmpty())
    }

    @Test
    fun `given all preset when accessed then equals KmpTarget all`() {
        assertEquals(KmpTarget.all, KmpTargetSet.all.members)
    }

    @Test
    fun `given appleMobile preset when accessed then contains exactly the iOS leaves`() {
        val expected =
            setOf<KmpTarget>(
                KmpTarget.Native.Apple.Ios.Arm64,
                KmpTarget.Native.Apple.Ios.SimulatorArm64,
            )
        assertEquals(expected, KmpTargetSet.appleMobile.members)
    }

    @Test
    fun `given appleDesktop preset when accessed then contains exactly the macOS leaves`() {
        val expected =
            setOf<KmpTarget>(KmpTarget.Native.Apple.Macos.Arm64, KmpTarget.Native.Apple.Macos.X64)
        assertEquals(expected, KmpTargetSet.appleDesktop.members)
    }

    @Test
    fun `given apple preset when accessed then equals appleMobile plus appleDesktop`() {
        val expected = KmpTargetSet.appleMobile.members + KmpTargetSet.appleDesktop.members
        assertEquals(expected, KmpTargetSet.apple.members)
    }

    @Test
    fun `given web preset when accessed then contains exactly Js WasmJs WasmWasi`() {
        val expected =
            setOf<KmpTarget>(KmpTarget.Web.Js, KmpTarget.Web.WasmJs, KmpTarget.Web.WasmWasi)
        assertEquals(expected, KmpTargetSet.web.members)
    }

    @Test
    fun `given jvmFamily preset when accessed then contains Android and Desktop`() {
        val expected = setOf<KmpTarget>(KmpTarget.Jvm.Android, KmpTarget.Jvm.Desktop)
        assertEquals(expected, KmpTargetSet.jvmFamily.members)
    }

    @Test
    fun `given of with varargs when constructed then contains every passed target`() {
        val set = KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Web.Js)
        assertEquals(setOf<KmpTarget>(KmpTarget.Jvm.Android, KmpTarget.Web.Js), set.members)
    }

    @Test
    fun `given of with duplicates when constructed then duplicates are collapsed`() {
        val set = KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Jvm.Android)
        assertEquals(1, set.size)
    }

    @Test
    fun `given a set when adding a target then result includes that target`() {
        val base = KmpTargetSet.of(KmpTarget.Jvm.Android)
        val result = base + KmpTarget.Web.Js
        assertEquals(setOf<KmpTarget>(KmpTarget.Jvm.Android, KmpTarget.Web.Js), result.members)
    }

    @Test
    fun `given a set when adding another set then result is the union`() {
        val a = KmpTargetSet.of(KmpTarget.Jvm.Android)
        val b = KmpTargetSet.of(KmpTarget.Web.Js, KmpTarget.Web.WasmJs)
        val result = a + b
        assertEquals(
            setOf<KmpTarget>(KmpTarget.Jvm.Android, KmpTarget.Web.Js, KmpTarget.Web.WasmJs),
            result.members,
        )
    }

    @Test
    fun `given a set when removing a target then result excludes that target`() {
        val base = KmpTargetSet.appleMobile
        val result = base - KmpTarget.Native.Apple.Ios.SimulatorArm64
        assertFalse(KmpTarget.Native.Apple.Ios.SimulatorArm64 in result)
        assertTrue(KmpTarget.Native.Apple.Ios.Arm64 in result)
    }

    @Test
    fun `given a set when removing another set then result is the difference`() {
        val base = KmpTargetSet.apple
        val result = base - KmpTargetSet.appleDesktop
        assertEquals(KmpTargetSet.appleMobile.members, result.members)
    }

    @Test
    fun `given a set when iterated then yields its members`() {
        val set = KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Web.Js)
        val collected = set.toList()
        assertEquals(2, collected.size)
        assertTrue(KmpTarget.Jvm.Android in collected)
        assertTrue(KmpTarget.Web.Js in collected)
    }

    @Test
    fun `given a set when contains is called then identity matching works on data objects`() {
        val set = KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64)
        assertTrue(KmpTarget.Native.Apple.Ios.Arm64 in set)
        assertFalse(KmpTarget.Native.Apple.Ios.SimulatorArm64 in set)
    }

    @Test
    fun `given plus minus chain when applied then operations compose left-to-right`() {
        val result =
            KmpTargetSet.appleMobile + KmpTarget.Jvm.Android -
                KmpTarget.Native.Apple.Ios.SimulatorArm64
        val expected = setOf<KmpTarget>(KmpTarget.Native.Apple.Ios.Arm64, KmpTarget.Jvm.Android)
        assertEquals(expected, result.members)
    }

    @Test
    fun `given two equal sets when compared then they are equal and have equal hashCode`() {
        val a = KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Web.Js)
        val b = KmpTargetSet.of(KmpTarget.Web.Js, KmpTarget.Jvm.Android)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
