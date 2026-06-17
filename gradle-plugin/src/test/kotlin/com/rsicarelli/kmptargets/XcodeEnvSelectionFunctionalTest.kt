package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof of the opt-in Xcode-environment selection source (#109) through
 * Gradle's *launcher* paths: with `kmptargets.xcodeEnv=true` committed, Xcode's real `SDK_NAME` /
 * `ARCHS` / `CONFIGURATION` env vars drive selection (no `-P`); an explicit `-P` still wins; an
 * unknown SDK falls through without failing the chain; and with the flag off the env is never read.
 * The per-layer precedence machinery itself is the shared `labeledSources` chain (proven for the
 * targets/build-types keys by [EnvPropertyMappingFunctionalTest] /
 * [FrameworkBuildTypesEnvPropertyMappingFunctionalTest]); this pins that the Xcode-env source rides
 * it at the right slot and maps Xcode's vocabulary correctly.
 */
class XcodeEnvSelectionFunctionalTest {

    @Test
    fun `given the flag on and an Xcode simulator env when the build runs then selection resolves to the simulator leaf`(
        @TempDir dir: Path
    ) {
        // The committed file selects jvm; the Xcode env (above the dedicated files) overrides it to
        // exactly the leaf Xcode is building — the zero-P win.
        writeSelectionFixture(dir, xcodeEnv = true, committedTargets = "jvm")
        val result =
            runner(dir)
                .withEnvironment(mapOf("SDK_NAME" to "iphonesimulator18.0", "ARCHS" to "arm64"))
                .build()
        assertTrue("SELECTION=iosSimulatorArm64" in result.output, result.output)
    }

    @Test
    fun `given an explicit -P targets over the Xcode env when the build runs then the -P wins`(
        @TempDir dir: Path
    ) {
        // SDK_NAME/ARCHS would map to iosArm64, but the CLI layer outranks the Xcode-env source.
        writeSelectionFixture(dir, xcodeEnv = true, committedTargets = "android")
        val result =
            runner(dir)
                .withArguments("printSelection", "-q", "-Pkmptargets.targets=jvm")
                .withEnvironment(mapOf("SDK_NAME" to "iphoneos18.0", "ARCHS" to "arm64"))
                .build()
        assertTrue("SELECTION=jvm" in result.output, result.output)
    }

    @Test
    fun `given the flag on and an unknown Xcode sdk when the build runs then it falls through to the committed file`(
        @TempDir dir: Path
    ) {
        writeSelectionFixture(dir, xcodeEnv = true, committedTargets = "jvm")
        val result =
            runner(dir)
                .withEnvironment(mapOf("SDK_NAME" to "driverkit24.0", "ARCHS" to "arm64"))
                .build()
        assertTrue("SELECTION=jvm" in result.output, result.output)
    }

    @Test
    fun `given the flag off and an Xcode env set when the build runs then the env is never read`(
        @TempDir dir: Path
    ) {
        writeSelectionFixture(dir, xcodeEnv = false, committedTargets = "jvm")
        val result =
            runner(dir)
                .withEnvironment(mapOf("SDK_NAME" to "iphonesimulator18.0", "ARCHS" to "arm64"))
                .build()
        assertTrue("SELECTION=jvm" in result.output, result.output)
    }

    @Test
    fun `given the flag on and a Release CONFIGURATION when the build configures then the framework narrows to RELEASE`(
        @TempDir dir: Path
    ) {
        writeFrameworkFixture(dir)
        val result =
            runner(dir)
                .withArguments("assertBuildTypes", "-q")
                .withEnvironment(mapOf("CONFIGURATION" to "Release"))
                .build()
        assertTrue("BUILDTYPES=RELEASE" in result.output, result.output)
    }

    @Test
    fun `given a custom CONFIGURATION with a KOTLIN_FRAMEWORK_BUILD_TYPE fallback when the build configures then the framework narrows to it`(
        @TempDir dir: Path
    ) {
        writeFrameworkFixture(dir)
        val result =
            runner(dir)
                .withArguments("assertBuildTypes", "-q")
                .withEnvironment(
                    mapOf("CONFIGURATION" to "Staging", "KOTLIN_FRAMEWORK_BUILD_TYPE" to "release")
                )
                .build()
        assertTrue("BUILDTYPES=RELEASE" in result.output, result.output)
    }

    private fun writeSelectionFixture(dir: Path, xcodeEnv: Boolean, committedTargets: String) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("kmp-targets.properties")
            .writeText(
                buildString {
                    if (xcodeEnv) appendLine("kmptargets.xcodeEnv=true")
                    appendLine("kmptargets.targets=$committedTargets")
                }
            )
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins { id("com.rsicarelli.kmptargets") }

                val selection = kmpTargets.resolvedSelection().members.map { it.id }.sorted()
                tasks.register("printSelection") {
                    doLast { println("SELECTION=" + selection.joinToString(",")) }
                }
                """
                    .trimIndent()
            )
    }

    private fun writeFrameworkFixture(dir: Path) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        // The flag is committed; an Apple leaf is committed so the framework attaches.
        // CONFIGURATION
        // then narrows the default DEBUG+RELEASE declaration through the same #108 intersection.
        dir.resolve("kmp-targets.properties")
            .writeText("kmptargets.xcodeEnv=true\nkmptargets.targets=iosSimulatorArm64\n")
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
                import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

                plugins {
                    kotlin("multiplatform")
                    id("com.rsicarelli.kmptargets")
                }

                kmpTargets {
                    supports { appleMobile }
                    appleFramework("KotlinShared")
                }

                val buildTypes =
                    kotlin.targets.withType(KotlinNativeTarget::class.java)
                        .flatMap { it.binaries.withType(Framework::class.java) }
                        .map { it.buildType.name }
                        .toSortedSet()
                println("BUILDTYPES=" + buildTypes.joinToString(","))
                tasks.register("assertBuildTypes")
                """
                    .trimIndent()
            )
    }

    private fun runner(dir: Path): GradleRunner =
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withArguments("printSelection", "-q")
}
