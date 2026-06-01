package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Proves the type-safe `kmpTargets { … }` DSL set during the build-script body (the escape-hatch
 * flow: KGP applied first, then the base plugin, then a narrowing `supported { … }`). This is the
 * case the old "timing wall" silently dropped: registration is deferred to the after-evaluate pass,
 * so a `supported`/`selection` declared in the body is honored. `(project as ProjectInternal)
 * .evaluate()` fires that pass in-process.
 */
class KmpTargetsDslTest {

    @Test
    fun `given supported declared via DSL after KGP when evaluated then only the DSL set registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=jvm,iosArm64,linuxX64\n")
        val project = newEvaluatedProject(dir) { ext -> ext.supported { jvm + linuxX64 } }
        // selection (jvm,iosArm64,linuxX64) ∩ supported (jvm,linuxX64) = jvm,linuxX64.
        assertRegisteredTargets(project, "jvm", "linuxX64")
    }

    @Test
    fun `given supported leaves composed via DSL when evaluated then the union registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=all\n")
        val project =
            newEvaluatedProject(dir) { ext -> ext.supported { iosArm64 + iosSimulatorArm64 + jvm } }
        assertRegisteredTargets(project, "iosArm64", "iosSimulatorArm64", "jvm")
    }

    @Test
    fun `given a per-module selection default and no global when evaluated then the default applies`(
        @TempDir dir: Path
    ) {
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supported { all }
                ext.selection { jvm + iosArm64 }
            }
        assertRegisteredTargets(project, "jvm", "iosArm64")
    }

    @Test
    fun `given a per-module selection default and a global KMP_TARGETS when evaluated then global wins`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=jvm\n")
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supported { all }
                ext.selection { iosArm64 + iosSimulatorArm64 } // ignored: global wins
            }
        assertRegisteredTargets(project, "jvm")
    }

    @Test
    fun `given supported via DSL value overload when evaluated then it registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=all\n")
        val project =
            newEvaluatedProject(dir) { ext -> ext.supported(KmpTargetSet.web + KmpTargetSet.linux) }
        assertRegisteredTargets(project, "js", "wasmJs", "wasmWasi", "linuxX64", "linuxArm64")
    }

    @Test
    fun `given supported block with subtraction when evaluated then the removed leaf does not register`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=all\n")
        val project = newEvaluatedProject(dir) { ext -> ext.supported { appleMobile - iosX64 } }
        assertRegisteredTargets(project, "iosArm64", "iosSimulatorArm64")
    }

    @Test
    fun `given a selection value default and a global KMP_TARGETS when evaluated then global wins`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=jvm\n")
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supported { all }
                ext.selection(KmpTargetSet.web) // ignored: global wins
            }
        assertRegisteredTargets(project, "jvm")
    }

    @Test
    fun `given a blank global and a selection default when evaluated then the default applies`(
        @TempDir dir: Path
    ) {
        // A blank KMP_TARGETS means "not overriding" — the per-module default selection stands.
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=\n")
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supported { all }
                ext.selection { jvm + linuxX64 }
            }
        assertRegisteredTargets(project, "jvm", "linuxX64")
    }

    @Test
    fun `given an empty selection default and no global when evaluated then nothing registers`(
        @TempDir dir: Path
    ) {
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supported { all }
                ext.selection { empty } // explicit "build nothing"
            }
        assertRegisteredTargets(project /* none */)
    }

    @Test
    fun `given a supported set disjoint from the selection when evaluated then nothing registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=wasmJs\n")
        val project = newEvaluatedProject(dir) { ext -> ext.supported { jvm + linuxX64 } }
        assertRegisteredTargets(project /* none — selection ∩ supported is empty */)
    }

    /**
     * Escape-hatch order: user applies KGP, then the base plugin, then configures the extension in
     * the body. The deferred registration runs only on [ProjectInternal.evaluate].
     */
    private fun newEvaluatedProject(dir: Path, configure: (KmpTargetsExtension) -> Unit): Project {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        configure(project.extensions.getByType(KmpTargetsExtension::class.java))
        (project as ProjectInternal).evaluate()
        return project
    }

    private fun assertRegisteredTargets(project: Project, vararg expected: String) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val actual = kotlin.targets.names.filter { it != "metadata" }.toSet()
        assertEquals(expected.toSet(), actual)
    }
}
