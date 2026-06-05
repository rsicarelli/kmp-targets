package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional (TestKit) proof that the dotted `kmptargets.targets` key survives Gradle's *launcher*
 * paths, which in-process `ProjectBuilder` tests cannot exercise:
 * - the `ORG_GRADLE_PROJECT_<name>` env→project-property mapping accepts a dotted name, and
 * - the `-P<name>=` CLI form resolves through the plugin's top precedence layer.
 *
 * Kept to the minimum (no KGP, one task) because TestKit forks a real build per case.
 */
class EnvPropertyMappingFunctionalTest {

    @Test
    fun `given ORG_GRADLE_PROJECT env var with dotted key when build runs then selection resolves from it`(
        @TempDir dir: Path
    ) {
        writeFixture(dir)
        val result =
            runner(dir)
                .withEnvironment(mapOf("ORG_GRADLE_PROJECT_kmptargets.targets" to "jvm"))
                .build()
        assertTrue("SELECTION=jvm" in result.output, result.output)
    }

    @Test
    fun `given dotted key passed via -P when build runs then selection resolves from it`(
        @TempDir dir: Path
    ) {
        // The committed file is also present, so this doubles as a launcher-level proof that the
        // CLI layer outranks the dedicated config file.
        writeFixture(dir)
        dir.resolve("kmp-targets.properties").writeText("kmptargets.targets=android\n")
        val result =
            runner(dir).withArguments("printSelection", "-q", "-Pkmptargets.targets=jvm").build()
        assertTrue("SELECTION=jvm" in result.output, result.output)
    }

    private fun writeFixture(dir: Path) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        dir.resolve("build.gradle.kts")
            .writeText(
                """
                plugins { id("com.rsicarelli.kmptargets") }

                val selection = kmpTargets.resolvedSelection().members.map { it.id }.sorted()
                tasks.register("printSelection") {
                    doLast { println("SELECTION=" + selection.joinToString(",")) }
                }
                """
                    .trimIndent()
            )
    }

    private fun runner(dir: Path): GradleRunner =
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withArguments("printSelection", "-q")
}
