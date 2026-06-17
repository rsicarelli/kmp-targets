package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The Apple framework helper (issue #105): [KmpTargetsExtension.appleFramework] declares a
 * framework once and attaches it to every registered Apple leaf, and
 * [KmpTargetsExtension.onAppleTarget] hands back the live KGP [KotlinNativeTarget] for arbitrary
 * per-target wiring. Both ride the existing `onRegistered` replay path, so they are
 * ordering-immune; neither re-declares KGP's `Framework` vocabulary — the configure block is the
 * real type.
 */
class AppleFrameworkInProcessTest {

    @Test
    fun `given appleFramework before supports when supports runs then it attaches to every apple leaf with the baseName`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,iosSimulatorArm64,jvm")
        project.kmpTargets().appleFramework("KotlinShared") { isStatic = true }
        project
            .kmpTargets()
            .supports(KmpTargetSet.appleMobile + KmpTargetSet.of(KmpTarget.Jvm.Desktop))

        val frameworks = project.frameworksByTarget()
        assertEquals(setOf("iosArm64", "iosSimulatorArm64"), frameworks.keys)
        frameworks.values.flatten().forEach {
            assertEquals("KotlinShared", it.baseName)
            assertTrue(it.isStatic, "configure block must run against the real Framework")
        }
    }

    @Test
    fun `given appleFramework after supports when declared then it attaches identically via replay`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,iosSimulatorArm64")
        project.kmpTargets().supports(KmpTargetSet.appleMobile)
        project.kmpTargets().appleFramework("KotlinShared")

        val frameworks = project.frameworksByTarget()
        assertEquals(setOf("iosArm64", "iosSimulatorArm64"), frameworks.keys)
        assertTrue(frameworks.values.flatten().all { it.baseName == "KotlinShared" })
    }

    @Test
    fun `given a second supports union when it adds an apple leaf then the framework attaches to the delta only`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,iosSimulatorArm64")
        project.kmpTargets().appleFramework("KotlinShared")
        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64))
        val firstPass = project.frameworksByTarget()
        assertEquals(setOf("iosArm64"), firstPass.keys)
        val arm64FrameworkCount = firstPass.getValue("iosArm64").size

        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.SimulatorArm64))
        val secondPass = project.frameworksByTarget()
        assertEquals(setOf("iosArm64", "iosSimulatorArm64"), secondPass.keys)
        // The already-attached leaf is not re-attached: its framework count is unchanged.
        assertEquals(
            arm64FrameworkCount,
            secondPass.getValue("iosArm64").size,
            "iosArm64 must not be re-attached by the second supports union",
        )
    }

    @Test
    fun `given a selection with non-apple leaves when appleFramework declared then only apple targets get a framework`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,jvm,linuxX64")
        project.kmpTargets().appleFramework("KotlinShared")
        project
            .kmpTargets()
            .supports(
                KmpTargetSet.of(
                    KmpTarget.Native.Apple.Ios.Arm64,
                    KmpTarget.Jvm.Desktop,
                    KmpTarget.Native.Linux.X64,
                )
            )
        assertEquals(setOf("iosArm64"), project.frameworksByTarget().keys)
    }

    @Test
    fun `given an on filter narrower than the registered apple leaves when declared then only the filtered leaves attach`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,macosArm64")
        // Supports both iOS and macOS, but the framework targets appleMobile only.
        project.kmpTargets().appleFramework("KotlinShared", on = KmpTargetSet.appleMobile)
        project
            .kmpTargets()
            .supports(
                KmpTargetSet.of(
                    KmpTarget.Native.Apple.Ios.Arm64,
                    KmpTarget.Native.Apple.Macos.Arm64,
                )
            )
        assertEquals(setOf("iosArm64"), project.frameworksByTarget().keys)
    }

    @Test
    fun `given buildTypes narrowed to DEBUG when declared then only the debug framework binary is created`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64")
        project
            .kmpTargets()
            .appleFramework("KotlinShared", buildTypes = listOf(NativeBuildType.DEBUG))
        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64))
        val frameworks = project.frameworksByTarget().getValue("iosArm64")
        assertEquals(listOf(NativeBuildType.DEBUG), frameworks.map { it.buildType })
    }

    @Test
    fun `given onAppleTarget when supports registers apple and non-apple leaves then it fires the live target for apple only`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,jvm,linuxX64")
        val seen = mutableMapOf<String, String>()
        project.kmpTargets().onAppleTarget { seen[targetName] = this::class.simpleName ?: "" }
        project
            .kmpTargets()
            .supports(
                KmpTargetSet.of(
                    KmpTarget.Native.Apple.Ios.Arm64,
                    KmpTarget.Jvm.Desktop,
                    KmpTarget.Native.Linux.X64,
                )
            )
        assertEquals(setOf("iosArm64"), seen.keys)
        assertTrue(
            seen.getValue("iosArm64").contains("Native"),
            "receiver must be a KotlinNativeTarget",
        )
    }

    @Test
    fun `given on not a subset of apple when appleFramework declared then it fails naming the offending ids`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64")
        val failure =
            assertFailsWith<org.gradle.api.GradleException> {
                project.kmpTargets().appleFramework("KotlinShared", on = KmpTargetSet.mobile)
            }
        // mobile = androidTarget + ios*; the offending non-apple id is androidTarget.
        assertTrue("androidTarget" in failure.message!!, failure.message!!)
    }

    @Test
    fun `given a blank baseName when appleFramework declared then it fails`(@TempDir dir: Path) {
        val project = projectWithSelection(dir, "iosArm64")
        assertFailsWith<org.gradle.api.GradleException> {
            project.kmpTargets().appleFramework("  ")
        }
    }

    @Test
    fun `given a second appleFramework call when declared then it fails loudly`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64")
        project.kmpTargets().appleFramework("KotlinShared")
        val failure =
            assertFailsWith<org.gradle.api.GradleException> {
                project.kmpTargets().appleFramework("Other")
            }
        assertTrue("at most once" in failure.message!!, failure.message!!)
    }

    @Test
    fun `given KGP absent when appleFramework and onAppleTarget are used then nothing fires and nothing crashes`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        var appleFired = false
        project.kmpTargets().onAppleTarget { appleFired = true }
        project.kmpTargets().appleFramework("KotlinShared")
        project.kmpTargets().supports(KmpTargetSet.appleMobile)
        assertEquals(false, appleFired)
        assertEquals(KmpTargetSet.empty, project.kmpTargets().registered())
    }

    @Test
    fun `given xcframework true and two ios leaves when declared then the assemble XCFramework tasks exist`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,iosSimulatorArm64")
        project.kmpTargets().appleFramework("KotlinShared", xcframework = true)
        project.kmpTargets().supports(KmpTargetSet.appleMobile)
        // KGP also registers per-family fat-framework intermediates, so assert the canonical three
        // are present rather than pinning the full set.
        val tasks = project.xcframeworkTaskNames()
        assertTrue("assembleKotlinSharedDebugXCFramework" in tasks, tasks.toString())
        assertTrue("assembleKotlinSharedReleaseXCFramework" in tasks, tasks.toString())
        assertTrue("assembleKotlinSharedXCFramework" in tasks, tasks.toString())
    }

    @Test
    fun `given xcframework true but a jvm-only selection when declared then no XCFramework task is registered`(
        @TempDir dir: Path
    ) {
        // Laziness: the config is created on the first Apple attach, which never happens here.
        val project = projectWithSelection(dir, "jvm")
        project.kmpTargets().appleFramework("KotlinShared", xcframework = true)
        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop))
        assertEquals(emptySet(), project.xcframeworkTaskNames())
    }

    @Test
    fun `given xcframework true with buildTypes narrowed to DEBUG when declared then only the debug assemble variant exists`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,iosSimulatorArm64")
        project
            .kmpTargets()
            .appleFramework(
                "KotlinShared",
                buildTypes = listOf(NativeBuildType.DEBUG),
                xcframework = true,
            )
        project.kmpTargets().supports(KmpTargetSet.appleMobile)
        val tasks = project.xcframeworkTaskNames()
        assertTrue("assembleKotlinSharedDebugXCFramework" in tasks, tasks.toString())
        assertTrue("assembleKotlinSharedReleaseXCFramework" !in tasks, tasks.toString())
    }

    @Test
    fun `given xcframework defaulting to false when declared then no XCFramework task is registered`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,iosSimulatorArm64")
        project.kmpTargets().appleFramework("KotlinShared")
        project.kmpTargets().supports(KmpTargetSet.appleMobile)
        assertEquals(emptySet(), project.xcframeworkTaskNames())
    }

    private fun projectWithSelection(dir: Path, selection: String): Project {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=$selection\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        return project
    }

    private fun Project.kmpTargets(): KmpTargetsExtension =
        extensions.getByType(KmpTargetsExtension::class.java)

    /** Every [Framework] binary the plugin attached, grouped by the KGP target name. */
    private fun Project.frameworksByTarget(): Map<String, List<Framework>> =
        extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .withType(KotlinNativeTarget::class.java)
            .associate { it.targetName to it.binaries.withType(Framework::class.java).toList() }
            .filterValues { it.isNotEmpty() }

    /** The registered task names KGP's XCFrameworkConfig contributes (assemble…XCFramework). */
    private fun Project.xcframeworkTaskNames(): Set<String> =
        tasks.names.filter { it.endsWith("XCFramework") }.toSet()
}
