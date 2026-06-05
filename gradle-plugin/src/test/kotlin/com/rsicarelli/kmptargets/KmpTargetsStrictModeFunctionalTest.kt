package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof of strict mode's configuration-cache contract, which in-process tests
 * cannot exercise: the flag is read once at apply time as a primitive `Boolean` (never capturing
 * `Project`), so a healthy configuration with strict ON must store a configuration-cache entry and
 * reuse it on the second run. KGP is applied for real here — the strict branch lives inside the
 * eager registration that only runs under `withPlugin(KGP)`.
 *
 * Kept to the minimum (one fixture, two builds) because a KGP-resolving TestKit build is the most
 * expensive kind.
 */
class KmpTargetsStrictModeFunctionalTest {

    @Test
    fun `given strict on and a healthy configuration when the build runs twice with configuration cache then the second run reuses the entry`(
        @TempDir dir: Path
    ) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm\nkmptargets.strict=true\n")
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.multiplatform")
                    id("com.rsicarelli.kmptargets")
                }

                kmpTargets { supports { jvm } }
                """
                    .trimIndent()
            )
        val runner =
            GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("help", "--configuration-cache")
        val first = runner.build()
        assertTrue("Configuration cache entry stored." in first.output, first.output)
        val second = runner.build()
        assertTrue("Reusing configuration cache." in second.output, second.output)
    }
}
