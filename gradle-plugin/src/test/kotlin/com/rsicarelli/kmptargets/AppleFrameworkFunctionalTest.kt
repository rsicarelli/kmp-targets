package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof that [KmpTargetsExtension.appleFramework] attaches a real KGP
 * framework — that the canonical link task (`linkDebugFrameworkIosSimulatorArm64`) materializes
 * with the declared `baseName` — and that doing so is **configuration-cache clean** (entry stored
 * then reused), which the in-process `ProjectBuilder` tests cannot observe. Configuration only: the
 * assert task reads the registered binaries at configuration time and prints primitives in its
 * action, so no Kotlin/Native toolchain is provisioned (the framework is never linked).
 */
class AppleFrameworkFunctionalTest {

    @Test
    fun `given appleFramework when the build configures then the iOS link task exists with the baseName and the config cache is reused`(
        @TempDir dir: Path
    ) {
        writeFixture(dir)

        val first = runner(dir).withArguments("assertFramework", "--configuration-cache").build()
        assertTrue("FRAMEWORK=iosSimulatorArm64:KotlinShared" in first.output, first.output)
        assertTrue("LINK_TASK_PRESENT=true" in first.output, first.output)
        assertTrue("Configuration cache entry stored" in first.output, first.output)

        // Second run: the framework wiring (resolver capturing the KMP extension, onRegistered
        // firing) contributed nothing un-serializable to the requested task graph, so the entry is
        // reused rather than rejected.
        val second = runner(dir).withArguments("assertFramework", "--configuration-cache").build()
        assertTrue("Configuration cache entry reused" in second.output, second.output)
    }

    @Test
    fun `given xcframework true when the build configures then the assemble XCFramework task exists and the config cache is reused`(
        @TempDir dir: Path
    ) {
        writeFixture(dir, xcframework = true)

        val first = runner(dir).withArguments("assertFramework", "--configuration-cache").build()
        assertTrue("XCFRAMEWORK_TASK_PRESENT=true" in first.output, first.output)
        assertTrue("Configuration cache entry stored" in first.output, first.output)

        // The lazily-created XCFrameworkConfig registers KGP-owned, config-cache-compatible tasks
        // and captures no Project of ours, so the entry is reused. (Never executed — xcodebuild is
        // macOS-only; we only assert the task exists.)
        val second = runner(dir).withArguments("assertFramework", "--configuration-cache").build()
        assertTrue("Configuration cache entry reused" in second.output, second.output)
    }

    private fun writeFixture(dir: Path, xcframework: Boolean = false) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=iosArm64,iosSimulatorArm64\n")
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
                    appleFramework("KotlinShared", xcframework = $xcframework) { isStatic = false }
                }

                // Asserted at configuration time and printed here (a build-script `doLast` would
                // capture the script object and trip the config cache on its own — unrelated to the
                // plugin). The requested `assertFramework` task carries no action, so the only
                // configuration-time work the cache must store is the plugin's framework wiring.
                kotlin.targets.withType(KotlinNativeTarget::class.java).forEach { t ->
                    t.binaries.withType(Framework::class.java).forEach {
                        println("FRAMEWORK=" + t.targetName + ":" + it.baseName)
                    }
                }
                println("LINK_TASK_PRESENT=" + (tasks.findByName("linkDebugFrameworkIosSimulatorArm64") != null))
                println("XCFRAMEWORK_TASK_PRESENT=" + (tasks.findByName("assembleKotlinSharedXCFramework") != null))
                tasks.register("assertFramework")
                """
                    .trimIndent()
            )
    }

    private fun runner(dir: Path): GradleRunner =
        GradleRunner.create().withProjectDir(dir.toFile()).withPluginClasspath()
}
