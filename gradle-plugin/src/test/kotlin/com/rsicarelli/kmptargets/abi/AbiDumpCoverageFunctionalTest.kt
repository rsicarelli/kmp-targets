package com.rsicarelli.kmptargets.abi

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof of the signal-only ABI-dump coverage annotation (#81): a module that
 * supports `jvm + linuxX64 + js` and carries committed dumps covering the two klib targets,
 * reported through Gradle's real launcher. Under a narrowed `jvm` selection the dumps cover targets
 * the build did not register, so `kmpTargetsDoctor` flags it and `kmpTargetsInfo` annotates it;
 * under the full selection the gap closes and the finding vanishes — the zero-noise common case.
 * The check is filesystem-only, so registering the native/js targets (no compilation happens in a
 * report run) needs no Kotlin/Native toolchain.
 */
class AbiDumpCoverageFunctionalTest {

    @Test
    fun `given dumps cover targets a narrowed selection did not register when kmpTargetsDoctor runs then it flags the gap`(
        @TempDir dir: Path
    ) {
        writeFixture(dir, selection = "jvm")
        val runner =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("kmpTargetsDoctor", "--configuration-cache")
        val first = runner.build()
        assertTrue("[!] ABI dumps not covered by this selection" in first.output, first.output)
        assertTrue("js, linuxX64" in first.output, first.output)
        assertTrue("checkKotlinAbi/apiCheck" in first.output, first.output)
        assertTrue("Configuration cache entry stored." in first.output, first.output)
        val second = runner.build()
        assertTrue("Reusing configuration cache." in second.output, second.output)
    }

    @Test
    fun `given dumps cover targets a narrowed selection did not register when kmpTargetsInfo runs then it annotates the coverage`(
        @TempDir dir: Path
    ) {
        writeFixture(dir, selection = "jvm")
        val result =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("kmpTargetsInfo", "-q")
                .build()
        assertTrue("ABI dumps (binary-compatibility)" in result.output, result.output)
        assertTrue("present:  covers js, linuxX64" in result.output, result.output)
        assertTrue(
            "js, linuxX64 not registered under this selection" in result.output,
            result.output,
        )
    }

    @Test
    fun `given the full selection registers every dumped target when kmpTargetsDoctor runs then there is no abi finding`(
        @TempDir dir: Path
    ) {
        writeFixture(dir, selection = "jvm,linuxX64,js")
        val result =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("kmpTargetsDoctor", "-q")
                .build()
        assertTrue("ABI dumps not covered" !in result.output, result.output)
    }

    private fun writeFixture(dir: Path, selection: String) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("gradle.properties").writeText("kmptargets.targets=$selection\n")
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.multiplatform")
                    id("com.rsicarelli.kmptargets")
                }

                repositories { mavenCentral() }

                kmpTargets { supports { jvm + linuxX64 + js } }
                """
                    .trimIndent()
            )
        val src = dir.resolve("src/commonMain/kotlin")
        src.createDirectories()
        src.resolve("Sample.kt").writeText("fun sample(): Int = 42\n")
        // Committed dump: a merged klib dump covering the two klib targets (jvm lives in a flat
        // .api,
        // which contributes presence only). Generated once under the full selection in a real
        // project.
        val api = dir.resolve("api")
        api.createDirectories()
        api.resolve("fixture.klib.api").writeText("// Klib ABI Dump\n// Targets: [js, linuxX64]\n")
    }
}
