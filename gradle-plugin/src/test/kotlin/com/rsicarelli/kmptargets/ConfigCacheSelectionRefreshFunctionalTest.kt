package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The "do I have to kill Gradle to switch targets?" proof.
 *
 * Two builds against the SAME project dir (so they share the on-disk configuration cache, exactly
 * like an IDE re-sync against a live daemon). Between them, only `kmp-targets.local.properties` is
 * edited. If the file is a tracked config-cache input, the second build must INVALIDATE the cache
 * and register the new target — no `gradlew stop`, no `--no-configuration-cache`.
 */
class ConfigCacheSelectionRefreshFunctionalTest {

    @Test
    fun `given a cached build when the local properties selection is edited then the next run invalidates the cache and registers the new target`(
        @TempDir dir: Path
    ) {
        writeFixture(dir)

        // Build 1: select jvm, store the configuration cache.
        dir.resolve("kmp-targets.local.properties").writeText("kmptargets.targets=jvm\n")
        val first = runner(dir).build()
        assertTrue("Configuration cache entry stored." in first.output, first.output)

        // Build 2: edit ONLY the file (no daemon restart, no -P). Must NOT reuse the cache, and the
        // newly selected target must now be the registered one.
        dir.resolve("kmp-targets.local.properties").writeText("kmptargets.targets=iosArm64\n")
        val second = runner(dir).build()
        assertFalse("Reusing configuration cache." in second.output, second.output)
        assertTrue("Registered (selection ∩ supported)" in second.output, second.output)
        assertTrue("iosArm64" in second.output, second.output)
    }

    private fun writeFixture(dir: Path) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.multiplatform")
                    id("com.rsicarelli.kmptargets")
                }

                repositories { mavenCentral() }

                kmpTargets { supports { jvm + iosArm64 } }
                """
                    .trimIndent()
            )
    }

    private fun runner(dir: Path): GradleRunner =
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withArguments("kmpTargetsInfo", "--configuration-cache")
}
