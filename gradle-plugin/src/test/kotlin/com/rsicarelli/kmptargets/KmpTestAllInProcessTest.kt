package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * In-process pins for the `kmpTestAll` umbrella task (#77): the test-side sibling of
 * [KmpCompileAllInProcessTest]. It depends on the test-run task (`<targetName>Test`) of every
 * registered target that has one — the [org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests]
 * targets only, so a device-only native (`iosArm64`) contributes nothing. Rename-proof: a jvm leaf
 * renamed via [KmpTargetsExtension.targetName] wires `desktopTest`, never a hardcoded `jvmTest`
 * that would silently match zero tasks.
 */
class KmpTestAllInProcessTest {

    @Test
    fun `given a full selection when the project evaluates then kmpTestAll depends on the test tasks of registered runnable targets`(
        @TempDir dir: Path
    ) {
        val project = evaluated(dir, "jvm,linuxX64") { supports { jvm + linuxX64 } }
        assertEquals(setOf("jvmTest", "linuxX64Test"), testAllDeps(project))
    }

    @Test
    fun `given a renamed jvm leaf when the project evaluates then kmpTestAll depends on the renamed test task`(
        @TempDir dir: Path
    ) {
        // The highest-stakes false-green: a hardcoded `jvmTest` never runs once jvm is renamed, so
        // CI passes while testing nothing. The umbrella wires `desktopTest` instead.
        val project =
            evaluated(dir, "jvm") {
                targetName(KmpTargetsDsl.jvm, "desktop")
                supports { jvm }
            }
        val deps = testAllDeps(project)
        assertTrue("desktopTest" in deps, "renamed leaf wires its real test task: $deps")
        assertFalse("jvmTest" in deps, "the default name must not be wired: $deps")
    }

    @Test
    fun `given a jvm-only narrowed lane when the project evaluates then kmpTestAll depends only on the jvm test task`(
        @TempDir dir: Path
    ) {
        val project = evaluated(dir, "jvm") { supports { jvm + linuxX64 } }
        assertEquals(setOf("jvmTest"), testAllDeps(project))
    }

    @Test
    fun `given a device-only native target when the project evaluates then kmpTestAll wires no test task for it`(
        @TempDir dir: Path
    ) {
        // iosArm64 is a device target — not KotlinTargetWithTests, so it has no test-run task. The
        // umbrella must skip it rather than wire a non-existent `iosArm64Test`. jvm still wires.
        val project = evaluated(dir, "jvm,iosArm64") { supports { jvm + iosArm64 } }
        assertEquals(setOf("jvmTest"), testAllDeps(project))
    }

    @Test
    fun `given an inert module when the project evaluates then kmpTestAll has zero dependencies`(
        @TempDir dir: Path
    ) {
        val project = evaluated(dir, "js") { supports { jvm } }
        assertEquals(emptySet(), testAllDeps(project))
    }

    @Test
    fun `given a module that never declares supports when the project evaluates then kmpTestAll exists with zero dependencies`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm\nkmptargets.umbrellaTasks=true\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        (project as ProjectInternal).evaluate()
        assertNotNull(project.tasks.findByName(KmpTargetsPlugin.TEST_ALL_TASK))
        assertEquals(emptySet(), testAllDeps(project))
    }

    private fun testAllDeps(project: Project): Set<String> {
        val task =
            project.tasks.findByName(KmpTargetsPlugin.TEST_ALL_TASK)
                ?: error("${KmpTargetsPlugin.TEST_ALL_TASK} not registered")
        return task.taskDependencies.getDependencies(task).map { it.name }.toSet()
    }

    private fun evaluated(
        dir: Path,
        selection: String,
        block: KmpTargetsExtension.() -> Unit,
    ): Project {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=$selection\nkmptargets.umbrellaTasks=true\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        project.extensions.getByType(KmpTargetsExtension::class.java).block()
        (project as ProjectInternal).evaluate()
        return project
    }
}
