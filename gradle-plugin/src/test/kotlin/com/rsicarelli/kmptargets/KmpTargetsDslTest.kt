package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
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
 * flow: KGP applied first, then the base plugin, then a narrowing `supports { … }`). This is the
 * case the old "timing wall" silently dropped: registration is deferred to the after-evaluate pass,
 * so a `supports` set declared in the body is honored. `(project as ProjectInternal).evaluate()`
 * fires that pass in-process.
 */
class KmpTargetsDslTest {

    @Test
    fun `given supports declared via DSL after KGP when evaluated then only the DSL set registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=jvm,iosArm64,linuxX64\n")
        val project = newEvaluatedProject(dir) { ext -> ext.supports { jvm + linuxX64 } }
        // selection (jvm,iosArm64,linuxX64) ∩ supported (jvm,linuxX64) = jvm,linuxX64.
        assertRegisteredTargets(project, "jvm", "linuxX64")
    }

    @Test
    fun `given supports leaves composed via DSL when evaluated then the union registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=all\n")
        val project =
            newEvaluatedProject(dir) { ext -> ext.supports { iosArm64 + iosSimulatorArm64 + jvm } }
        assertRegisteredTargets(project, "iosArm64", "iosSimulatorArm64", "jvm")
    }

    @Test
    fun `given a defaultSelection and no global when evaluated then the default applies`(
        @TempDir dir: Path
    ) {
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supports { all }
                ext.defaultSelection.set(
                    KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64)
                )
            }
        assertRegisteredTargets(project, "jvm", "iosArm64")
    }

    @Test
    fun `given a defaultSelection and a global KMP_TARGETS when evaluated then global wins`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=jvm\n")
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supports { all }
                ext.defaultSelection.set(KmpTargetSet.appleMobile) // ignored: global wins
            }
        assertRegisteredTargets(project, "jvm")
    }

    @Test
    fun `given supports via DSL value overload when evaluated then it registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=all\n")
        val project =
            newEvaluatedProject(dir) { ext -> ext.supports(KmpTargetSet.web + KmpTargetSet.linux) }
        assertRegisteredTargets(project, "js", "wasmJs", "wasmWasi", "linuxX64", "linuxArm64")
    }

    @Test
    fun `given supports block with subtraction when evaluated then the removed leaf does not register`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=all\n")
        val project = newEvaluatedProject(dir) { ext -> ext.supports { appleMobile - iosX64 } }
        assertRegisteredTargets(project, "iosArm64", "iosSimulatorArm64")
    }

    @Test
    fun `given a blank global and a defaultSelection when evaluated then the default applies`(
        @TempDir dir: Path
    ) {
        // A blank KMP_TARGETS means "not overriding" — the project-wide defaultSelection stands.
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=\n")
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supports { all }
                ext.defaultSelection.set(
                    KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Linux.X64)
                )
            }
        assertRegisteredTargets(project, "jvm", "linuxX64")
    }

    @Test
    fun `given an empty defaultSelection and no global when evaluated then nothing registers`(
        @TempDir dir: Path
    ) {
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supports { all }
                ext.defaultSelection.set(KmpTargetSet.empty) // explicit "build nothing"
            }
        assertRegisteredTargets(project /* none */)
    }

    @Test
    fun `given a supports set disjoint from the selection when evaluated then nothing registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=wasmJs\n")
        val project = newEvaluatedProject(dir) { ext -> ext.supports { jvm + linuxX64 } }
        assertRegisteredTargets(project /* none — selection ∩ supported is empty */)
    }

    @Test
    fun `given no DSL body and a global KMP_TARGETS when evaluated then selection registers under default-all supported`(
        @TempDir dir: Path
    ) {
        // No `kmpTargets { … }` body at all — supported is undeclared, so `resolvedSupported`
        // returns its default of `all`. The global selection alone decides what registers. This is
        // the floor of the API contract: applying the base plugin with no configuration must still
        // honor KMP_TARGETS as the single switch.
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=jvm,iosArm64\n")
        val project = newEvaluatedProject(dir) { /* no extension configuration */ }
        assertRegisteredTargets(project, "jvm", "iosArm64")
    }

    @Test
    fun `given supports mobile plus web and a narrowing global KMP_TARGETS when evaluated then exactly the overlap registers`(
        @TempDir dir: Path
    ) {
        // Composition test: the DSL takes the place of stacking two convention plugin ids (e.g.
        // `.mobile` + `.web`). `supports { mobile + web }` is the equivalent declaration; with a
        // selection that hits one leaf from each preset, only those leaves register.
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=iosArm64,wasmJs\n")
        val project = newEvaluatedProject(dir) { ext -> ext.supports { mobile + web } }
        assertRegisteredTargets(project, "iosArm64", "wasmJs")
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
