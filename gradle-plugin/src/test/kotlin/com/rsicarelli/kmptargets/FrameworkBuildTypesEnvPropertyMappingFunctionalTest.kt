package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof that the dotted `kmptargets.framework.buildTypes` key (#108) resolves
 * through Gradle's *launcher* paths exactly like `kmptargets.targets` — the env→project-property
 * mapping accepts the doubly-dotted name, the `-P` CLI form outranks the committed config file —
 * and that the resolved value narrows the **real** KGP `Framework.buildType` set. A junk value
 * fails the launcher build with the parser's did-you-mean. The per-layer precedence machinery
 * itself is the shared `labeledSources` chain (proven for the selection key by
 * [EnvPropertyMappingFunctionalTest]); this pins that the new key rides it and reaches the real
 * binaries.
 */
class FrameworkBuildTypesEnvPropertyMappingFunctionalTest {

    @Test
    fun `given ORG_GRADLE_PROJECT env var with the dotted build-types key when the build configures then the framework narrows to it`(
        @TempDir dir: Path
    ) {
        writeFixture(dir)
        val result =
            runner(dir)
                .withArguments("assertBuildTypes", "-q")
                .withEnvironment(
                    mapOf("ORG_GRADLE_PROJECT_kmptargets.framework.buildTypes" to "debug")
                )
                .build()
        assertTrue("BUILDTYPES=DEBUG" in result.output, result.output)
    }

    @Test
    fun `given the build-types key via -P over a committed file when the build configures then the CLI wins and the framework narrows to it`(
        @TempDir dir: Path
    ) {
        // The committed file sets debug; the CLI sets release — a launcher-level proof that the CLI
        // layer outranks the dedicated config file for the new key, exactly as for the targets key.
        writeFixture(dir)
        dir.resolve("kmp-targets.properties").writeText("kmptargets.framework.buildTypes=debug\n")
        val result =
            runner(dir)
                .withArguments(
                    "assertBuildTypes",
                    "-q",
                    "-Pkmptargets.framework.buildTypes=release",
                )
                .build()
        assertTrue("BUILDTYPES=RELEASE" in result.output, result.output)
    }

    @Test
    fun `given a junk build-types value via -P when the build runs then it fails with a did-you-mean`(
        @TempDir dir: Path
    ) {
        writeFixture(dir)
        val result =
            runner(dir)
                .withArguments("assertBuildTypes", "-Pkmptargets.framework.buildTypes=relese")
                .buildAndFail()
        assertTrue("did you mean 'release'?" in result.output, result.output)
    }

    private fun writeFixture(dir: Path) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosSimulatorArm64\n")
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
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

                // Read the effective build types off the real KGP Framework binaries at
                // configuration time and print them; the requested task carries no action, so the
                // narrowing is the only configuration-time work under test.
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
        GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath()
}
