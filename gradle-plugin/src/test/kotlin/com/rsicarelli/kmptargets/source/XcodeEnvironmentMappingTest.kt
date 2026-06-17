package com.rsicarelli.kmptargets.source

import com.rsicarelli.kmptargets.model.KmpTarget
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.junit.jupiter.api.Test

/**
 * Table-pinned unit tests for the pure `SDK_NAME`+`ARCHS` → leaf and `CONFIGURATION` → build-type
 * mappings behind the opt-in Xcode-environment source (#109). The env-var contract is the only
 * stable surface KGP exposes, so this table IS the spec — any drift fails here, not in a build.
 */
class XcodeEnvironmentMappingTest {

    @Test
    fun `given an iphoneos device sdk with arm64 when mapped then it is iosArm64`() {
        assertEquals(KmpTarget.Native.Apple.Ios.Arm64, xcodeTargetLeaf("iphoneos18.0", "arm64"))
    }

    @Test
    fun `given an iphonesimulator sdk with arm64 when mapped then it is iosSimulatorArm64`() {
        assertEquals(
            KmpTarget.Native.Apple.Ios.SimulatorArm64,
            xcodeTargetLeaf("iphonesimulator18.0", "arm64"),
        )
    }

    @Test
    fun `given an iphonesimulator sdk with x86_64 when mapped then it is iosX64`() {
        assertEquals(
            KmpTarget.Native.Apple.Ios.X64,
            xcodeTargetLeaf("iphonesimulator18.0", "x86_64"),
        )
    }

    @Test
    fun `given the tvos device and simulator sdks when mapped then they are the tvos leaves`() {
        assertEquals(KmpTarget.Native.Apple.Tvos.Arm64, xcodeTargetLeaf("appletvos18.0", "arm64"))
        assertEquals(
            KmpTarget.Native.Apple.Tvos.SimulatorArm64,
            xcodeTargetLeaf("appletvsimulator18.0", "arm64"),
        )
        assertEquals(
            KmpTarget.Native.Apple.Tvos.X64,
            xcodeTargetLeaf("appletvsimulator18.0", "x86_64"),
        )
    }

    @Test
    fun `given the watchos device archs when mapped then each is its distinct watch leaf`() {
        assertEquals(
            KmpTarget.Native.Apple.Watchos.DeviceArm64,
            xcodeTargetLeaf("watchos11.0", "arm64"),
        )
        assertEquals(
            KmpTarget.Native.Apple.Watchos.Arm64,
            xcodeTargetLeaf("watchos11.0", "arm64_32"),
        )
        assertEquals(KmpTarget.Native.Apple.Watchos.Arm32, xcodeTargetLeaf("watchos11.0", "armv7k"))
    }

    @Test
    fun `given the watchsimulator sdk when mapped then it is the watch simulator leaves`() {
        assertEquals(
            KmpTarget.Native.Apple.Watchos.SimulatorArm64,
            xcodeTargetLeaf("watchsimulator11.0", "arm64"),
        )
        assertEquals(
            KmpTarget.Native.Apple.Watchos.X64,
            xcodeTargetLeaf("watchsimulator11.0", "x86_64"),
        )
    }

    @Test
    fun `given the macosx sdk when mapped then it is the macos leaves`() {
        assertEquals(KmpTarget.Native.Apple.Macos.Arm64, xcodeTargetLeaf("macosx15.0", "arm64"))
        assertEquals(KmpTarget.Native.Apple.Macos.X64, xcodeTargetLeaf("macosx15.0", "x86_64"))
    }

    @Test
    fun `given a recognized sdk in a different case when mapped then the match is case-insensitive`() {
        assertEquals(
            KmpTarget.Native.Apple.Ios.SimulatorArm64,
            xcodeTargetLeaf("iPhoneSimulator18.0", "ARM64"),
        )
    }

    @Test
    fun `given an unknown sdk when mapped then it falls through to null`() {
        assertNull(xcodeTargetLeaf("driverkit24.0", "arm64"))
        assertNull(xcodeTargetLeaf("", "arm64"))
        assertNull(xcodeTargetLeaf(null, "arm64"))
    }

    @Test
    fun `given a known sdk with an unrecognized arch when mapped then it falls through to null`() {
        assertNull(xcodeTargetLeaf("iphoneos18.0", "i386"))
        assertNull(xcodeTargetLeaf("iphonesimulator18.0", null))
        assertNull(xcodeTargetLeaf("iphonesimulator18.0", "  "))
    }

    @Test
    fun `given a multi-arch fat ARCHS when mapped then it is out of scope and falls through to null`() {
        assertNull(xcodeTargetLeaf("iphonesimulator18.0", "arm64 x86_64"))
    }

    @Test
    fun `given Debug or Release CONFIGURATION when mapped then it is the matching build type`() {
        assertEquals(NativeBuildType.DEBUG, xcodeBuildType("Debug", null))
        assertEquals(NativeBuildType.RELEASE, xcodeBuildType("Release", null))
    }

    @Test
    fun `given a CONFIGURATION in a different case when mapped then the match is case-insensitive`() {
        assertEquals(NativeBuildType.DEBUG, xcodeBuildType("DEBUG", null))
        assertEquals(NativeBuildType.RELEASE, xcodeBuildType(" release ", null))
    }

    @Test
    fun `given a custom CONFIGURATION with a KOTLIN_FRAMEWORK_BUILD_TYPE fallback when mapped then the fallback wins`() {
        assertEquals(NativeBuildType.RELEASE, xcodeBuildType("Staging", "release"))
        assertEquals(NativeBuildType.DEBUG, xcodeBuildType("QA", "DEBUG"))
    }

    @Test
    fun `given a custom CONFIGURATION with no usable fallback when mapped then it falls through to null`() {
        assertNull(xcodeBuildType("Staging", null))
        assertNull(xcodeBuildType("Staging", "preview"))
        assertNull(xcodeBuildType(null, null))
    }
}
