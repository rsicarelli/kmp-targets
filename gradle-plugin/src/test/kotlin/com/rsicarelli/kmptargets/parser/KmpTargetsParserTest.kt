package com.rsicarelli.kmptargets.parser

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class KmpTargetsParserTest {

    @Test
    fun `given empty string when parsed then result is Ok empty with warning`() {
        val result = parseKmpTargets("")
        assertTrue(result is ParseResult.Ok, "expected Ok, got $result")
        assertEquals(KmpTargetSet.empty, result.set)
        assertTrue(result.warnings.isNotEmpty(), "expected at least one warning, got none")
    }

    @Test
    fun `given whitespace-only string when parsed then result is Ok empty with warning`() {
        val result = parseKmpTargets("   \t  ")
        assertTrue(result is ParseResult.Ok, "expected Ok, got $result")
        assertEquals(KmpTargetSet.empty, result.set)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `given a single canonical id when parsed then result contains exactly that leaf`() {
        val result = parseKmpTargets("iosArm64")
        assertTrue(result is ParseResult.Ok, "got $result")
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), result.set)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `given a user-friendly alias when parsed then result resolves to canonical leaf`() {
        val result = parseKmpTargets("android")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Android), result.set)
    }

    @Test
    fun `given the all preset when parsed then result equals KmpTargetSet all`() {
        val result = parseKmpTargets("all")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.all, result.set)
    }

    @Test
    fun `given the appleMobile preset when parsed then result equals KmpTargetSet appleMobile`() {
        val result = parseKmpTargets("appleMobile")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.appleMobile, result.set)
    }

    @Test
    fun `given the apple preset when parsed then result equals KmpTargetSet apple`() {
        val result = parseKmpTargets("apple")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.apple, result.set)
    }

    @Test
    fun `given the web preset when parsed then result equals KmpTargetSet web`() {
        val result = parseKmpTargets("web")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.web, result.set)
    }

    @Test
    fun `given multiple comma-separated leaves when parsed then result contains the union`() {
        val result = parseKmpTargets("android,iosArm64")
        assertTrue(result is ParseResult.Ok)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Native.Apple.Ios.Arm64),
            result.set,
        )
    }

    @Test
    fun `given a token with plus prefix when parsed then it is added to the running set`() {
        val result = parseKmpTargets("appleMobile,+android")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.appleMobile + KmpTarget.Jvm.Android, result.set)
    }

    @Test
    fun `given a token with minus prefix when parsed then it is removed from the running set`() {
        val result = parseKmpTargets("appleMobile,-iosSimulatorArm64")
        assertTrue(result is ParseResult.Ok)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64, KmpTarget.Native.Apple.Ios.X64),
            result.set,
        )
    }

    @Test
    fun `given mixed-case input when parsed then matching is case-insensitive`() {
        val result = parseKmpTargets("ANDROID,IOSARM64")
        assertTrue(result is ParseResult.Ok)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Native.Apple.Ios.Arm64),
            result.set,
        )
    }

    @Test
    fun `given whitespace around tokens when parsed then whitespace is trimmed`() {
        val result = parseKmpTargets("  android  ,  iosArm64  ")
        assertTrue(result is ParseResult.Ok)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Native.Apple.Ios.Arm64),
            result.set,
        )
    }

    @Test
    fun `given duplicate tokens when parsed then duplicates collapse`() {
        val result = parseKmpTargets("android,android")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Android), result.set)
    }

    @Test
    fun `given unknown token close to a known one when parsed then Err includes did-you-mean suggestion`() {
        val result = parseKmpTargets("androd")
        assertTrue(result is ParseResult.Err, "expected Err, got $result")
        assertEquals("androd", result.offendingToken)
        assertTrue(
            result.message.contains("android", ignoreCase = true),
            "expected did-you-mean to suggest 'android', got: ${result.message}",
        )
    }

    @Test
    fun `given unknown token with no close match when parsed then result is Err without suggestion`() {
        val result = parseKmpTargets("xboxOne")
        assertTrue(result is ParseResult.Err)
        assertEquals("xboxOne", result.offendingToken)
    }

    @Test
    fun `given bare family name when parsed then result is Err with helpful family hint`() {
        val result = parseKmpTargets("ios")
        assertTrue(result is ParseResult.Err, "expected Err for bare family name, got $result")
        assertNotNull(result.offendingToken)
        assertTrue(
            result.message.contains("appleMobile", ignoreCase = true) ||
                result.message.contains("iosArm64") ||
                result.message.contains("family", ignoreCase = true),
            "expected family hint, got: ${result.message}",
        )
    }

    @Test
    fun `given subtraction that empties a preset when parsed then result is Ok empty`() {
        val result = parseKmpTargets("appleMobile,-iosArm64,-iosSimulatorArm64,-iosX64")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.empty, result.set)
    }

    @Test
    fun `given preset and addition and subtraction when parsed then operations compose left-to-right`() {
        val result = parseKmpTargets("appleMobile,+android,-iosSimulatorArm64")
        assertTrue(result is ParseResult.Ok)
        assertEquals(
            KmpTargetSet.of(
                KmpTarget.Native.Apple.Ios.Arm64,
                KmpTarget.Native.Apple.Ios.X64,
                KmpTarget.Jvm.Android,
            ),
            result.set,
        )
    }

    @Test
    fun `given hyphenated alias when parsed then result resolves to canonical leaf`() {
        val result = parseKmpTargets("ios-arm64")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), result.set)
    }

    @Test
    fun `given a sign with no name when parsed then result is Err`() {
        val result = parseKmpTargets("android,+")
        assertTrue(result is ParseResult.Err, "expected Err for dangling sign, got $result")
    }

    @Test
    fun `given a leading minus when parsed then subtraction starts from empty as a no-op`() {
        val result = parseKmpTargets("-iosArm64")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.empty, result.set)
    }

    @Test
    fun `given comma-separated empties between tokens when parsed then empties are skipped`() {
        val result = parseKmpTargets("android,,iosArm64,")
        assertTrue(result is ParseResult.Ok)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Native.Apple.Ios.Arm64),
            result.set,
        )
    }

    @Test
    fun `given input with only separators when parsed then result is Ok empty with warning`() {
        val result = parseKmpTargets(",,,")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.empty, result.set)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `given the native preset when parsed then result equals KmpTargetSet native`() {
        val result = parseKmpTargets("native")
        assertTrue(result is ParseResult.Ok, "got $result")
        assertEquals(KmpTargetSet.native, result.set)
    }

    @Test
    fun `given the appleWatch and appleTv presets when parsed then they resolve to their sets`() {
        assertEquals(KmpTargetSet.appleWatch, (parseKmpTargets("appleWatch") as ParseResult.Ok).set)
        assertEquals(KmpTargetSet.appleTv, (parseKmpTargets("appleTv") as ParseResult.Ok).set)
    }

    @Test
    fun `given the linux preset when parsed then it resolves and is not treated as an unknown family`() {
        val result = parseKmpTargets("linux")
        assertTrue(result is ParseResult.Ok, "expected linux to resolve as a preset, got $result")
        assertEquals(KmpTargetSet.linux, result.set)
    }

    @Test
    fun `given the mingw preset and its windows alias when parsed then both resolve to mingw`() {
        assertEquals(KmpTargetSet.mingw, (parseKmpTargets("mingw") as ParseResult.Ok).set)
        assertEquals(KmpTargetSet.mingw, (parseKmpTargets("windows") as ParseResult.Ok).set)
    }

    @Test
    fun `given the androidNative preset when parsed then result equals KmpTargetSet androidNative`() {
        val result = parseKmpTargets("androidNative")
        assertTrue(result is ParseResult.Ok)
        assertEquals(KmpTargetSet.androidNative, result.set)
    }

    @Test
    fun `given new leaf ids when parsed then they resolve to their leaves`() {
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.X64),
            (parseKmpTargets("iosX64") as ParseResult.Ok).set,
        )
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Watchos.DeviceArm64),
            (parseKmpTargets("watchosDeviceArm64") as ParseResult.Ok).set,
        )
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.AndroidNative.Arm64),
            (parseKmpTargets("androidNativeArm64") as ParseResult.Ok).set,
        )
    }

    @Test
    fun `given a hyphenated alias for a new leaf when parsed then it resolves`() {
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Linux.X64),
            (parseKmpTargets("linux-x64") as ParseResult.Ok).set,
        )
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.AndroidNative.Arm64),
            (parseKmpTargets("android-native-arm64") as ParseResult.Ok).set,
        )
    }

    @Test
    fun `given bare watchos family when parsed then Err hints at appleWatch`() {
        val result = parseKmpTargets("watchos")
        assertTrue(result is ParseResult.Err, "expected Err for bare family, got $result")
        assertTrue(
            result.message.contains("appleWatch") || result.message.contains("watchosArm64"),
            "expected appleWatch hint, got: ${result.message}",
        )
    }

    @Test
    fun `given custom registry without a target when parsed then that target id is unknown`() {
        // Custom registry that excludes iosArm64
        val customRegistry: Set<KmpTarget> = KmpTarget.all - KmpTarget.Native.Apple.Ios.Arm64
        val result = parseKmpTargets("iosArm64", customRegistry)
        assertTrue(
            result is ParseResult.Err,
            "expected Err when token not in registry, got $result",
        )
        assertEquals("iosArm64", result.offendingToken)
    }
}
