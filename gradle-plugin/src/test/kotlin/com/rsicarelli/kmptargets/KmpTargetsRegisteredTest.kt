package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.model.RegisteredTarget
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The registered-targets query surface (issue #52): [KmpTargetsExtension.registered] snapshots and
 * the [KmpTargetsExtension.onRegistered] per-leaf hook let build-logic wire per-target things
 * (canonically KSP's `ksp<Target>` configurations) off what this plugin actually registered —
 * without importing KGP konan types or re-deriving name mangling.
 */
class KmpTargetsRegisteredTest {

    @Test
    fun `given supports mobile under a narrowed selection when registered is read then it equals selection intersect supported`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,jvm")
        project.kmpTargets().supports(KmpTargetSet.mobile)
        // mobile = androidTarget + ios*; the selection narrows it to iosArm64 (jvm is selected but
        // not supported here).
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
            project.kmpTargets().registered(),
        )
    }

    @Test
    fun `given a slice argument when registered appleMobile is read then only the apple-mobile leaves return`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,iosSimulatorArm64,jvm")
        project
            .kmpTargets()
            .supports(KmpTargetSet.appleMobile + KmpTargetSet.of(KmpTarget.Jvm.Desktop))
        assertEquals(
            KmpTargetSet.of(
                KmpTarget.Native.Apple.Ios.Arm64,
                KmpTarget.Native.Apple.Ios.SimulatorArm64,
            ),
            project.kmpTargets().registered(KmpTargetSet.appleMobile),
        )
    }

    @Test
    fun `given onRegistered hooked before supports when supports runs then the action fires for every registered leaf`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "jvm,iosArm64")
        val seen = mutableListOf<RegisteredTarget>()
        project.kmpTargets().onRegistered { seen += it }
        project
            .kmpTargets()
            .supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64))
        assertEquals(
            setOf(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64),
            seen.map { it.leaf }.toSet(),
        )
        assertEquals(setOf("jvm", "iosArm64"), seen.map { it.gradleName }.toSet())
    }

    @Test
    fun `given onRegistered hooked after supports when hooked then it fires immediately for already-registered leaves`(
        @TempDir dir: Path
    ) {
        // configureEach semantics: a convention plugin must not care whether it ran before or
        // after the module's supports { } — late hooks replay the log.
        val project = projectWithSelection(dir, "jvm,iosArm64")
        project
            .kmpTargets()
            .supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64))
        val seen = mutableListOf<RegisteredTarget>()
        project.kmpTargets().onRegistered { seen += it }
        assertEquals(
            setOf(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64),
            seen.map { it.leaf }.toSet(),
        )
    }

    @Test
    fun `given two supports calls when the union registers a new leaf then onRegistered fires for the delta only`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "jvm,iosArm64,linuxX64")
        val seen = mutableListOf<RegisteredTarget>()
        project.kmpTargets().onRegistered { seen += it }
        project
            .kmpTargets()
            .supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64))
        assertEquals(2, seen.size, seen.toString())
        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Native.Linux.X64))
        assertEquals(3, seen.size, "second supports must fire only the delta: $seen")
        assertEquals(KmpTarget.Native.Linux.X64, seen.last().leaf)
    }

    @Test
    fun `given KGP absent when registered and onRegistered are used then nothing registered and nothing fires`(
        @TempDir dir: Path
    ) {
        // Documented semantics: registered() mirrors what actually registered with KGP — not the
        // abstract selection ∩ supported plan. Without KGP nothing registers, so the snapshot is
        // empty and the hook never fires (and nothing crashes).
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64,jvm\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val seen = mutableListOf<RegisteredTarget>()
        project.kmpTargets().onRegistered { seen += it }
        project.kmpTargets().supports(KmpTargetSet.mobile)
        assertEquals(KmpTargetSet.empty, project.kmpTargets().registered())
        assertEquals(emptyList(), seen)
    }

    @Test
    fun `given the ksp use case when wiring config names via gradleNameCapitalized then the names match KGP's`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "iosArm64,iosSimulatorArm64")
        val kspConfigurations = mutableSetOf<String>()
        project.kmpTargets().onRegistered { kspConfigurations += "ksp${it.gradleNameCapitalized}" }
        project.kmpTargets().supports(KmpTargetSet.appleMobile)
        val expected =
            project.kotlinTargetNames().map { "ksp${it.replaceFirstChar(Char::titlecase)}" }.toSet()
        assertEquals(expected, kspConfigurations)
        assertTrue("kspIosArm64" in kspConfigurations, kspConfigurations.toString())
    }

    @Test
    fun `given onRegistered when wiring via configurationName then the names match KGP's`(
        @TempDir dir: Path
    ) {
        // Same proof as the gradleNameCapitalized test above, but through the issue #74 helpers:
        // configurationName / testConfigurationName must derive exactly the ksp<Target>[Test] names
        // KGP would mangle, so consumers stop hand-rolling the string.
        val project = projectWithSelection(dir, "iosArm64,iosSimulatorArm64")
        val mainConfigs = mutableSetOf<String>()
        val testConfigs = mutableSetOf<String>()
        project.kmpTargets().onRegistered {
            mainConfigs += it.configurationName("ksp")
            testConfigs += it.testConfigurationName("ksp")
        }
        project.kmpTargets().supports(KmpTargetSet.appleMobile)
        val expectedMain =
            project.kotlinTargetNames().map { "ksp${it.replaceFirstChar(Char::titlecase)}" }.toSet()
        assertEquals(expectedMain, mainConfigs)
        assertEquals(expectedMain.map { it + "Test" }.toSet(), testConfigs)
        assertTrue("kspIosArm64" in mainConfigs, mainConfigs.toString())
        assertTrue("kspIosArm64Test" in testConfigs, testConfigs.toString())
    }

    @Test
    fun `given a renamed jvm when configurationName helpers fire then they follow the custom name`(
        @TempDir dir: Path
    ) {
        // The regression the issue reports: a hardcoded "kspJvmTest" breaks under targetName; the
        // helpers track the renamed gradleName into "kspDesktop" / "kspDesktopTest".
        val project = projectWithSelection(dir, "jvm")
        project.kmpTargets().targetName(KmpTargetsDsl.jvm, "desktop")
        val seen = mutableListOf<RegisteredTarget>()
        project.kmpTargets().onRegistered { seen += it }
        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop))
        assertEquals("kspDesktop", seen.single().configurationName("ksp"))
        assertEquals("kspDesktopTest", seen.single().testConfigurationName("ksp"))
    }

    @Test
    fun `given a renamed jvm when onRegistered fires then gradleName is the custom name`(
        @TempDir dir: Path
    ) {
        // Interplay with targetName (issue #49): the carrier reports what registration actually
        // called — never a name re-derived from the leaf.
        val project = projectWithSelection(dir, "jvm")
        project.kmpTargets().targetName(KmpTargetsDsl.jvm, "desktop")
        val seen = mutableListOf<RegisteredTarget>()
        project.kmpTargets().onRegistered { seen += it }
        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop))
        assertEquals(listOf(KmpTarget.Jvm.Desktop), seen.map { it.leaf })
        assertEquals("desktop", seen.single().gradleName)
        assertEquals("Desktop", seen.single().gradleNameCapitalized)
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

    private fun Project.kotlinTargetNames(): Set<String> =
        extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .names
            .filter { it != "metadata" }
            .toSet()
}
