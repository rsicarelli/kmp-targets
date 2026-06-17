package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof of `kmpTargetsDoctor` (#82) that in-process tests cannot give: the
 * report running through Gradle's real launcher, the configuration-cache contract (captures no
 * `Project`, so a healthy build stores then reuses an entry), and — the headline of the closure
 * half (#80) — a real cross-project, file-only closure check resolving clean under **Isolated
 * Projects**, where any peer-`Project` read would fail to configure.
 *
 * Kept to three KGP-resolving fixtures because that build is the most expensive kind.
 */
class KmpTargetsDoctorFunctionalTest {

    @Test
    fun `given a healthy jvm module when kmpTargetsDoctor runs twice with configuration cache then it reports a clean bill and reuses the entry`(
        @TempDir dir: Path
    ) {
        writeSingleModule(dir, supports = "jvm", selection = "jvm")
        val runner =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("kmpTargetsDoctor", "--configuration-cache")
        val first = runner.build()
        assertTrue("Configuration cache entry stored." in first.output, first.output)
        assertTrue("clean bill of health - registered: jvm" in first.output, first.output)
        assertTrue("[!]" !in first.output, first.output)
        val second = runner.build()
        assertTrue("Reusing configuration cache." in second.output, second.output)
    }

    @Test
    fun `given a disjoint selection when kmpTargetsDoctor runs then it explains the inert module`(
        @TempDir dir: Path
    ) {
        writeSingleModule(dir, supports = "jvm", selection = "wasmJs")
        val result =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("kmpTargetsDoctor", "-q")
                .build()
        assertTrue("[!] inert module" in result.output, result.output)
        assertTrue("commonMain metadata compilation" in result.output, result.output)
        assertTrue("gate on registered().isEmpty()" in result.output, result.output)
    }

    @Test
    fun `given a project-edge gap under isolated projects when kmpTargetsDoctor runs then it flags the gap names the clean edge and prints the limits`(
        @TempDir dir: Path
    ) {
        writeClosureFixture(dir)
        val runner =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments(
                    ":app-mobile:kmpTargetsDoctor",
                    ":app-jvm:kmpTargetsDoctor",
                    "--configuration-cache",
                )
        val first = runner.build()
        // The gap: :app-mobile needs iosArm64 + jvm; :lib-jvm registers jvm only.
        assertTrue("[!] :app-mobile → :lib-jvm" in first.output, first.output)
        assertTrue("missing: iosArm64" in first.output, first.output)
        // The false-positive guard: :app-jvm needs jvm; :lib-jvm registers jvm — a clean edge.
        assertTrue("no gaps" in first.output, first.output)
        // The two permanent, honest limits.
        assertTrue("external" in first.output, first.output)
        assertTrue("approximate" in first.output, first.output)
        // Isolated Projects implies the configuration cache: the cross-project, file-only closure
        // resolves and stores cleanly, then reuses — the proof no peer Project is ever read.
        assertTrue("Configuration cache entry stored." in first.output, first.output)
        val second = runner.build()
        assertTrue("Reusing configuration cache." in second.output, second.output)
    }

    private fun writeSingleModule(dir: Path, supports: String, selection: String) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("gradle.properties").writeText("kmptargets.targets=$selection\n")
        dir.resolve("build.gradle.kts").writeText(moduleScript(supports))
        commonSource(dir)
    }

    private fun writeClosureFixture(dir: Path) {
        dir.resolve("settings.gradle.kts")
            .writeText(
                """
                rootProject.name = "fixture"
                include(":lib-jvm", ":app-mobile", ":app-jvm")
                """
                    .trimIndent()
            )
        dir.resolve("gradle.properties")
            .writeText(
                "kmptargets.targets=iosArm64,jvm\n" +
                    "org.gradle.unsafe.isolated-projects=true\n" +
                    "org.gradle.caching=true\n"
            )
        dir.resolve("build.gradle.kts").writeText("// root: no cross-project configuration\n")
        module(dir, "lib-jvm", supports = "jvm", dependsOn = null)
        module(dir, "app-mobile", supports = "iosArm64 + jvm", dependsOn = ":lib-jvm")
        module(dir, "app-jvm", supports = "jvm", dependsOn = ":lib-jvm")
    }

    private fun module(dir: Path, name: String, supports: String, dependsOn: String?) {
        val moduleDir = dir.resolve(name)
        moduleDir.createDirectories()
        moduleDir.resolve("build.gradle.kts").writeText(moduleScript(supports, dependsOn))
        commonSource(moduleDir)
    }

    private fun moduleScript(supports: String, dependsOn: String? = null): String {
        val dep =
            if (dependsOn == null) ""
            else
                "\nkotlin { sourceSets { commonMain.dependencies " +
                    "{ implementation(project(\"$dependsOn\")) } } }\n"
        return """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("com.rsicarelli.kmptargets")
            }

            repositories { mavenCentral() }

            kmpTargets { supports { $supports } }
            $dep
            """
            .trimIndent()
    }

    private fun commonSource(moduleDir: Path) {
        val src = moduleDir.resolve("src/commonMain/kotlin")
        src.createDirectories()
        src.resolve("Sample.kt").writeText("fun sample(): Int = 42\n")
    }
}
