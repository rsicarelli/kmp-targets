package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof of the ABI × selection narrowing advisory (#81). The hook keys off the
 * task **name**, so the fixture registers a plain stand-in task named `apiCheck` — no ABI plugin or
 * Kotlin/Native toolchain needed: the point under test is the hook, not the tool. A module
 * supporting `jvm + linuxX64 + js` run under a narrowed `jvm` lane warns naming the uncovered
 * targets; under the full selection it is silent; under strict mode the run fails.
 */
class AbiNarrowingAdvisoryFunctionalTest {

    @Test
    fun `given a narrowed selection when an abi task runs then it warns naming the uncovered targets`(
        @TempDir dir: Path
    ) {
        writeFixture(dir, selection = "jvm")
        val result =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("apiCheck")
                .build()
        assertTrue(
            "is running 'apiCheck' under a narrowed selection" in result.output,
            result.output,
        )
        assertTrue("js, linuxX64 are NOT dumped/validated" in result.output, result.output)
    }

    @Test
    fun `given a same-ABI-group sibling is registered when an abi task runs then it stays silent`(
        @TempDir dir: Path
    ) {
        // iosArm64 + iosSimulatorArm64 supported, narrowed to the simulator: the iOS ABI is still
        // covered by the registered simulator, so the advisory must NOT fire for iosArm64.
        writeFixture(
            dir,
            supports = "iosArm64 + iosSimulatorArm64",
            selection = "iosSimulatorArm64",
        )
        val result =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("apiCheck")
                .build()
        assertTrue("narrowed selection" !in result.output, result.output)
    }

    @Test
    fun `given the full selection when an abi task runs then it stays silent`(@TempDir dir: Path) {
        writeFixture(dir, selection = "jvm,linuxX64,js")
        val result =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("apiCheck")
                .build()
        assertTrue("narrowed selection" !in result.output, result.output)
    }

    @Test
    fun `given strict mode and a narrowed selection when an abi task runs then the build fails`(
        @TempDir dir: Path
    ) {
        writeFixture(dir, selection = "jvm", strict = true)
        val result =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("apiCheck")
                .buildAndFail()
        assertTrue(
            "is running 'apiCheck' under a narrowed selection" in result.output,
            result.output,
        )
    }

    private fun writeFixture(
        dir: Path,
        selection: String,
        supports: String = "jvm + linuxX64 + js",
        strict: Boolean = false,
    ) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("gradle.properties")
            .writeText(
                "kmptargets.targets=$selection\n" + if (strict) "kmptargets.strict=true\n" else ""
            )
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.multiplatform")
                    id("com.rsicarelli.kmptargets")
                }

                repositories { mavenCentral() }

                kmpTargets { supports { $supports } }

                // Stand-in for an ABI tool's umbrella task — the hook matches on the name.
                tasks.register("apiCheck") { group = "verification" }
                """
                    .trimIndent()
            )
        val src = dir.resolve("src/commonMain/kotlin")
        src.createDirectories()
        src.resolve("Sample.kt").writeText("fun sample(): Int = 42\n")
    }
}
