package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The real-KSP tripwire for #73, complementing [CommonMainMetadataCompilationInvariantTest] (which
 * pins KGP's `compileCommonMainKotlinMetadata`). With an actual KSP plugin applied, it asserts that
 * KSP's own `kspCommonMainKotlinMetadata` route follows the **same** ≥2-platform-targets rule — the
 * fact the ktorfit sample first measured and that the old "needs a native target" recipe got wrong:
 *
 * - `jvm,js` (two platform targets, **zero native**) → the task **exists** (codegen route present).
 * - `jvm` alone (single target) → the task is **absent** (no shared commonMain metadata route).
 *
 * Task existence is read from `tasks --all` at configuration time, so nothing compiles (no native,
 * no processor execution) — cross-host safe and cheap. If a KGP/KSP bump moves this rule, the test
 * fails loudly and the recipe + the `single-target KSP` doctor finding must be revisited.
 */
class CommonMainKspRouteFunctionalTest {

    @Test
    fun `given a jvm and js selection with KSP applied when listing tasks then the commonMain KSP route exists despite no native`(
        @TempDir dir: Path
    ) {
        writeKspModule(dir, supports = "jvm + js", selection = "jvm,js")
        val output = tasksAll(dir)
        assertTrue("kspCommonMainKotlinMetadata" in output, output)
    }

    @Test
    fun `given a single jvm selection with KSP applied when listing tasks then the commonMain KSP route is absent`(
        @TempDir dir: Path
    ) {
        writeKspModule(dir, supports = "jvm + js", selection = "jvm")
        val output = tasksAll(dir)
        assertFalse("kspCommonMainKotlinMetadata" in output, output)
    }

    private fun tasksAll(dir: Path): String =
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withArguments("tasks", "--all", "-q")
            .build()
            .output

    private fun writeKspModule(dir: Path, supports: String, selection: String) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("gradle.properties").writeText("kmptargets.targets=$selection\n")
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.multiplatform")
                    id("com.google.devtools.ksp")
                    id("com.rsicarelli.kmptargets")
                }

                repositories { mavenCentral(); google() }

                kmpTargets { supports { $supports } }
                """
                    .trimIndent()
            )
    }
}
