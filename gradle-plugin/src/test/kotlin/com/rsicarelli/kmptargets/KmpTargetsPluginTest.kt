package com.rsicarelli.kmptargets

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KmpTargetsPluginTest {

    @Test
    fun `given plugin applied when running kmpTargetsHello then prints message`(@TempDir projectDir: Path) {
        writeProject(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("kmpTargetsHello", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":kmpTargetsHello")?.outcome)
        assertTrue(
            result.output.contains("kmp-targets plugin applied to :"),
            "Expected hello message in output, got:\n${result.output}",
        )
    }

    @Test
    fun `given plugin applied when listing tasks then kmpTargetsHello appears under kmp-targets group`(@TempDir projectDir: Path) {
        writeProject(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks", "--group", "kmp-targets")
            .build()

        assertTrue(
            result.output.contains("kmpTargetsHello"),
            "Expected kmpTargetsHello task listed, got:\n${result.output}",
        )
    }

    private fun writeProject(projectDir: Path) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"under-test\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.rsicarelli.kmptargets")
            }
            """.trimIndent(),
        )
    }
}
