package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * In-process pins for the `kmpCompileAll` umbrella task (#77): it must depend on the compile tasks
 * of **exactly the registered targets** — selection-agnostic and rename-proof — and never on the
 * commonMain metadata compilation (so it can't re-introduce the inert (#71) / JVM-less-fragment
 * (#72) failures).
 *
 * Wiring is asserted through `task.taskDependencies.getDependencies(task)` after `evaluate()` (no
 * compile, so native leaves are cross-host safe). The dependency names are the genuine KGP compile
 * tasks: `compileKotlin<Target>`, e.g. `compileKotlinDesktop` for a jvm leaf renamed via
 * [KmpTargetsExtension.targetName].
 */
class KmpCompileAllInProcessTest {

    @Test
    fun `given a full selection when the project evaluates then kmpCompileAll depends on every registered target main compile task`(
        @TempDir dir: Path
    ) {
        val project =
            evaluated(dir, "jvm,iosArm64,linuxX64") { supports { jvm + iosArm64 + linuxX64 } }
        assertEquals(
            setOf("compileKotlinJvm", "compileKotlinIosArm64", "compileKotlinLinuxX64"),
            compileAllDeps(project),
        )
    }

    @Test
    fun `given a jvm-only narrowed lane when the project evaluates then kmpCompileAll depends only on the jvm compile task`(
        @TempDir dir: Path
    ) {
        // Supports two leaves but the selection narrows to jvm: the umbrella wires only what
        // registered — no ios dependency, so no 404 under the narrowed lane.
        val project = evaluated(dir, "jvm") { supports { jvm + iosArm64 } }
        assertEquals(setOf("compileKotlinJvm"), compileAllDeps(project))
    }

    @Test
    fun `given a renamed jvm leaf when the project evaluates then kmpCompileAll depends on the renamed compile task`(
        @TempDir dir: Path
    ) {
        val project =
            evaluated(dir, "jvm") {
                targetName(KmpTargetsDsl.jvm, "desktop")
                supports { jvm }
            }
        val deps = compileAllDeps(project)
        assertTrue(
            "compileKotlinDesktop" in deps,
            "renamed leaf wires its real compile task: $deps",
        )
        assertFalse("compileKotlinJvm" in deps, "the default name must not be wired: $deps")
    }

    @Test
    fun `given a renamed jvm leaf when the project evaluates then the wiring is a real task not a zero match`(
        @TempDir dir: Path
    ) {
        // The headline regression: a hardcoded `compileKotlinJvm` would silently match zero tasks
        // once jvm is renamed. The umbrella wires off the live target, so the dependency is the one
        // real task — non-empty and exact.
        val project =
            evaluated(dir, "jvm") {
                targetName(KmpTargetsDsl.jvm, "desktop")
                supports { jvm }
            }
        assertEquals(setOf("compileKotlinDesktop"), compileAllDeps(project))
    }

    @Test
    fun `given an inert module when the project evaluates then kmpCompileAll has zero dependencies and not the metadata compilation`(
        @TempDir dir: Path
    ) {
        // Selection disjoint from supports → nothing registers (inert, #71). KGP still materializes
        // the doomed commonMain metadata compilation, but the umbrella must not pull it in.
        val project = evaluated(dir, "js") { supports { jvm } }
        val deps = compileAllDeps(project)
        assertTrue(deps.isEmpty(), "an inert module wires nothing: $deps")
        assertFalse(COMMON_MAIN_METADATA in deps, "never the metadata compilation")
    }

    @Test
    fun `given a native-only fragment when the project evaluates then kmpCompileAll depends on platform compile tasks but not the metadata compilation`(
        @TempDir dir: Path
    ) {
        // Two native leaves share a real commonMain, so KGP creates the metadata compilation (#72
        // territory). The umbrella depends on the platform klib compile tasks only — never
        // metadata.
        val project = evaluated(dir, "iosArm64,linuxX64") { supports { iosArm64 + linuxX64 } }
        assertNotNull(
            project.tasks.findByName(COMMON_MAIN_METADATA),
            "two shared targets create the metadata compilation — the case worth excluding",
        )
        val deps = compileAllDeps(project)
        assertEquals(setOf("compileKotlinIosArm64", "compileKotlinLinuxX64"), deps)
        assertFalse(COMMON_MAIN_METADATA in deps, "platform compilations only, never metadata")
    }

    @Test
    fun `given a module that never declares supports when the project evaluates then kmpCompileAll exists with zero dependencies`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm\nkmptargets.umbrellaTasks=true\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        (project as ProjectInternal).evaluate()
        assertEquals(emptySet(), compileAllDeps(project), "no supports declared → clean no-op")
    }

    @Test
    fun `given KGP absent when the project evaluates then kmpCompileAll still exists as a no-op`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm\nkmptargets.umbrellaTasks=true\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        (project as ProjectInternal).evaluate()
        assertNotNull(
            project.tasks.findByName(KmpTargetsPlugin.COMPILE_ALL_TASK),
            "with the flag on it is registered in every module so root fan-out never 404s",
        )
        assertEquals(emptySet(), compileAllDeps(project))
    }

    @Test
    fun `given the umbrellaTasks flag is unset when the project evaluates then the umbrella tasks are not registered`(
        @TempDir dir: Path
    ) {
        // Opt-in (#77): without `kmptargets.umbrellaTasks=true` neither task exists — the default
        // build is untouched, no extra dependency edges.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        project.extensions.getByType(KmpTargetsExtension::class.java).supports { jvm }
        (project as ProjectInternal).evaluate()
        assertNull(
            project.tasks.findByName(KmpTargetsPlugin.COMPILE_ALL_TASK),
            "compile umbrella off",
        )
        assertNull(project.tasks.findByName(KmpTargetsPlugin.TEST_ALL_TASK), "test umbrella off")
    }

    private fun compileAllDeps(project: Project): Set<String> {
        val task =
            project.tasks.findByName(KmpTargetsPlugin.COMPILE_ALL_TASK)
                ?: error("${KmpTargetsPlugin.COMPILE_ALL_TASK} not registered")
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

    private companion object {
        const val COMMON_MAIN_METADATA = "compileCommonMainKotlinMetadata"
    }
}
