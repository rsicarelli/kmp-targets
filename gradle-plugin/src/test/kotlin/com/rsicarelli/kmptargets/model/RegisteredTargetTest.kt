package com.rsicarelli.kmptargets.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

/**
 * The per-target configuration-name helpers (issue #74): [RegisteredTarget.configurationName] /
 * [RegisteredTarget.testConfigurationName] own the `prefix + gradleNameCapitalized [+ "Test"]`
 * grammar so build-logic stops hand-rolling it — and stay truthful under a renamed jvm leaf, the
 * footgun a hardcoded `"kspJvmTest"` falls into. Pure value tests on the immutable carrier.
 */
class RegisteredTargetTest {

    @Test
    fun `given a jvm registration when configurationName ksp then it is kspJvm`() {
        assertEquals(
            "kspJvm",
            RegisteredTarget(KmpTarget.Jvm.Desktop, "jvm").configurationName("ksp"),
        )
    }

    @Test
    fun `given a jvm registration when testConfigurationName ksp then it is kspJvmTest`() {
        assertEquals(
            "kspJvmTest",
            RegisteredTarget(KmpTarget.Jvm.Desktop, "jvm").testConfigurationName("ksp"),
        )
    }

    @Test
    fun `given a desktop-renamed jvm when configurationName ksp then it is kspDesktop`() {
        // targetName(jvm, "desktop") makes gradleName == "desktop"; the helper must follow it.
        assertEquals(
            "kspDesktop",
            RegisteredTarget(KmpTarget.Jvm.Desktop, "desktop").configurationName("ksp"),
        )
    }

    @Test
    fun `given a desktop-renamed jvm when testConfigurationName ksp then it is kspDesktopTest`() {
        // The motivating regression: hardcoded "kspJvmTest" threw "Configuration not found" under
        // the rename; derived from gradleName it correctly becomes "kspDesktopTest".
        assertEquals(
            "kspDesktopTest",
            RegisteredTarget(KmpTarget.Jvm.Desktop, "desktop").testConfigurationName("ksp"),
        )
    }

    @Test
    fun `given an iosArm64 registration when configurationName ksp then it is kspIosArm64`() {
        // Multi-segment name: only the first char is title-cased, the rest is preserved verbatim.
        assertEquals(
            "kspIosArm64",
            RegisteredTarget(KmpTarget.Native.Apple.Ios.Arm64, "iosArm64").configurationName("ksp"),
        )
    }

    @Test
    fun `given an arbitrary tool prefix when configurationName kapt then it is kaptJvm`() {
        // Tool-agnostic: the helper blesses no single tool's convention.
        assertEquals(
            "kaptJvm",
            RegisteredTarget(KmpTarget.Jvm.Desktop, "jvm").configurationName("kapt"),
        )
    }

    @Test
    fun `given a blank prefix when configurationName then it throws IllegalArgumentException`() {
        val target = RegisteredTarget(KmpTarget.Jvm.Desktop, "jvm")
        assertFailsWith<IllegalArgumentException> { target.configurationName("") }
        assertFailsWith<IllegalArgumentException> { target.configurationName("   ") }
    }

    @Test
    fun `given a blank prefix when testConfigurationName then it throws IllegalArgumentException`() {
        // Validation is delegated to configurationName, so it must hold on the test variant too.
        val target = RegisteredTarget(KmpTarget.Jvm.Desktop, "jvm")
        assertFailsWith<IllegalArgumentException> { target.testConfigurationName("") }
        assertFailsWith<IllegalArgumentException> { target.testConfigurationName("   ") }
    }

    @Test
    fun `given a single-char gradleName when configurationName then the char is title-cased`() {
        assertEquals("kspX", RegisteredTarget(KmpTarget.Jvm.Desktop, "x").configurationName("ksp"))
    }

    @Test
    fun `given an already-capitalized gradleName when configurationName then capitalization is unchanged`() {
        // titlecase of an already-upper first char is a no-op; the rest of the string is untouched.
        assertEquals(
            "kspDesktop",
            RegisteredTarget(KmpTarget.Jvm.Desktop, "Desktop").configurationName("ksp"),
        )
    }
}
