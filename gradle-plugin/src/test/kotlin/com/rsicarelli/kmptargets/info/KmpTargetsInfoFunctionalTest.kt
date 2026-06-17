package com.rsicarelli.kmptargets.info

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof of what in-process tests cannot exercise: the `kmpTargetsInfo` task
 * running through Gradle's real launcher with the configuration cache on.
 *
 * - The second invocation must be a configuration-cache HIT — the task's inputs are primitives
 *   resolved at configuration time, so nothing in it can poison the cache entry (issue #33).
 * - A build that never requests the task must also store cleanly — lazy registration means the
 *   unrequested task is never configured, so its providers never realize (the isolated-projects
 *   sample relies on this).
 *
 * Kept to the minimum because TestKit forks a real build per case.
 */
class KmpTargetsInfoFunctionalTest {

    @Test
    fun `given configuration cache enabled when kmpTargetsInfo runs twice then the second run reuses the cache entry`(
        @TempDir dir: Path
    ) {
        writeFixture(dir)
        val first = runner(dir).build()
        assertTrue("Configuration cache entry stored." in first.output, first.output)
        val second = runner(dir).build()
        assertTrue("Reusing configuration cache." in second.output, second.output)
        assertTrue("kmp-targets — :" in second.output, second.output)
    }

    @Test
    fun `given selection via -P mobile when kmpTargetsInfo runs then the output names the mobile leaves and the command line source`(
        @TempDir dir: Path
    ) {
        writeFixture(dir)
        val result = runner(dir, "kmpTargetsInfo", "-q", "-Pkmptargets.targets=mobile").build()
        assertTrue(
            "androidTarget, iosArm64, iosSimulatorArm64, iosX64" in result.output,
            result.output,
        )
        assertTrue("source:   command line (-Pkmptargets.targets)" in result.output, result.output)
    }

    @Test
    fun `given selection via ORG_GRADLE_PROJECT env var when kmpTargetsInfo runs then the output names the environment source`(
        @TempDir dir: Path
    ) {
        writeFixture(dir)
        val result =
            runner(dir, "kmpTargetsInfo", "-q")
                .withEnvironment(mapOf("ORG_GRADLE_PROJECT_kmptargets.targets" to "jvm"))
                .build()
        assertTrue(
            "source:   environment variable ORG_GRADLE_PROJECT_kmptargets.targets" in result.output,
            result.output,
        )
    }

    @Test
    fun `given the Xcode-env flag on and an Xcode sdk when kmpTargetsInfo runs then the output names the Xcode-environment source`(
        @TempDir dir: Path
    ) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("kmp-targets.properties")
            .writeText("kmptargets.xcodeEnv=true\nkmptargets.targets=jvm\n")
        dir.resolve("build.gradle.kts").writeText("plugins { id(\"com.rsicarelli.kmptargets\") }\n")
        val result =
            runner(dir, "kmpTargetsInfo", "-q")
                .withEnvironment(mapOf("SDK_NAME" to "iphonesimulator18.0", "ARCHS" to "arm64"))
                .build()
        assertTrue("iosSimulatorArm64" in result.output, result.output)
        assertTrue("source:   Xcode environment (SDK_NAME/ARCHS)" in result.output, result.output)
    }

    @Test
    fun `given a build that never requests the info task when run with configuration cache then the entry stores cleanly`(
        @TempDir dir: Path
    ) {
        writeFixture(dir)
        val result = runner(dir, "help", "--configuration-cache").build()
        assertTrue("Configuration cache entry stored." in result.output, result.output)
    }

    private fun writeFixture(dir: Path) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("build.gradle.kts").writeText("plugins { id(\"com.rsicarelli.kmptargets\") }\n")
    }

    private fun runner(dir: Path, vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withArguments(
                *(arguments.takeIf { it.isNotEmpty() }
                    ?: arrayOf("kmpTargetsInfo", "--configuration-cache"))
            )
}
