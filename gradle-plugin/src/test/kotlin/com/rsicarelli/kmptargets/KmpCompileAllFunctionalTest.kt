package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof of the `kmpCompileAll` / `kmpTestAll` umbrellas (#77) that in-process
 * tests cannot give: the configuration-cache contract (the umbrellas capture no `Project`, so a
 * healthy build stores and reuses an entry) and the end-to-end rename-proofing — under
 * `targetName(jvm,"desktop")` the umbrellas drive the **real** `compileKotlinDesktop` /
 * `desktopTest` tasks, the exact tasks a hardcoded `compileKotlinJvm` / `jvmTest` would have
 * silently missed.
 *
 * Kept to two fixtures because a KGP-resolving TestKit build is the most expensive kind.
 */
class KmpCompileAllFunctionalTest {

    @Test
    fun `given a healthy jvm configuration when kmpCompileAll runs twice with configuration cache then the second run reuses the entry`(
        @TempDir dir: Path
    ) {
        writeFixture(dir, jvmName = null, supports = "jvm")
        val runner =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("kmpCompileAll", "--configuration-cache")
        val first = runner.build()
        assertTrue("Configuration cache entry stored." in first.output, first.output)
        assertNotNull(first.task(":compileKotlinJvm"), "kmpCompileAll wired the jvm compile task")
        val second = runner.build()
        assertTrue("Reusing configuration cache." in second.output, second.output)
    }

    @Test
    fun `given a renamed jvm leaf when the umbrellas run then the renamed compile and test tasks execute`(
        @TempDir dir: Path
    ) {
        // The headline regression made executable: a hardcoded `compileKotlinJvm` / `jvmTest` would
        // match nothing once jvm is renamed. The umbrellas drive `compileKotlinDesktop` /
        // `desktopTest` instead — proven by them appearing in the executed task graph.
        writeFixture(dir, jvmName = "desktop", supports = "jvm")
        val result =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("kmpCompileAll", "kmpTestAll")
                .build()
        assertNotNull(
            result.task(":compileKotlinDesktop"),
            "kmpCompileAll drove the renamed compile task:\n${result.output}",
        )
        assertTrue(
            result.task(":compileKotlinDesktop")?.outcome == TaskOutcome.SUCCESS,
            result.output,
        )
        assertNotNull(
            result.task(":desktopTest"),
            "kmpTestAll drove the renamed test task:\n${result.output}",
        )
    }

    private fun writeFixture(dir: Path, jvmName: String?, supports: String) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("gradle.properties").writeText("kmptargets.targets=$supports\n")
        val rename =
            if (jvmName != null) "targetName(KmpTargetsDsl.jvm, \"$jvmName\")\n    " else ""
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                import com.rsicarelli.kmptargets.KmpTargetsDsl

                plugins {
                    id("org.jetbrains.kotlin.multiplatform")
                    id("com.rsicarelli.kmptargets")
                }

                repositories { mavenCentral() }

                kmpTargets {
                    ${rename}supports { $supports }
                }
                """
                    .trimIndent()
            )
        val src = dir.resolve("src/commonMain/kotlin")
        src.createDirectories()
        src.resolve("Sample.kt").writeText("fun sample(): Int = 42\n")
    }
}
