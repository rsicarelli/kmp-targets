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
 * flow: KGP applied first, then the base plugin, then a narrowing `supports { … }`). Registration
 * is **eager** — it fires the moment `supports { … }` runs, so targets exist before `(project as
 * ProjectInternal).evaluate()` is pumped (the pump only drives KGP's own hierarchy lifecycle).
 * `defaultSelection`/`hierarchyTemplate` must therefore be set *before* `supports`.
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
                ext.defaultSelection.set(
                    KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Apple.Ios.Arm64)
                )
                ext.supports { all }
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
                ext.defaultSelection.set(KmpTargetSet.appleMobile) // ignored: global wins
                ext.supports { all }
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
                ext.defaultSelection.set(
                    KmpTargetSet.of(KmpTarget.Jvm.Desktop, KmpTarget.Native.Linux.X64)
                )
                ext.supports { all }
            }
        assertRegisteredTargets(project, "jvm", "linuxX64")
    }

    @Test
    fun `given an empty defaultSelection and no global when evaluated then nothing registers`(
        @TempDir dir: Path
    ) {
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.defaultSelection.set(KmpTargetSet.empty) // explicit "build nothing"
                ext.supports { all }
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
    fun `given no supports declared and a global KMP_TARGETS when evaluated then nothing registers`(
        @TempDir dir: Path
    ) {
        // Targets are explicit: with no `supports { … }` there is nothing to register, regardless
        // of
        // KMP_TARGETS. This is the floor of the API contract under eager registration — there is no
        // implicit default-all supported set.
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=jvm,iosArm64\n")
        val project = newEvaluatedProject(dir) { /* no extension configuration */ }
        assertRegisteredTargets(project /* none — supported was never declared */)
    }

    @Test
    fun `given an explicit supports all and a global KMP_TARGETS when evaluated then the selection registers`(
        @TempDir dir: Path
    ) {
        // `supports { all }` is the explicit opt-in to "build everything KMP_TARGETS selects": with
        // supported = all, the global selection alone decides what registers.
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=jvm,iosArm64\n")
        val project = newEvaluatedProject(dir) { ext -> ext.supports { all } }
        assertRegisteredTargets(project, "jvm", "iosArm64")
    }

    @Test
    fun `given supports called twice when evaluated then each target registers at most once`(
        @TempDir dir: Path
    ) {
        // Eager registration is idempotent and unions: the second call registers only the delta
        // (iosArm64); jvm, already registered, is not re-added and KGP does not throw.
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=all\n")
        val project =
            newEvaluatedProject(dir) { ext ->
                ext.supports { jvm }
                ext.supports { jvm + iosArm64 }
            }
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
     * the body — where `supports { … }` registers eagerly. [ProjectInternal.evaluate] is pumped
     * only to drive KGP's own source-set hierarchy lifecycle.
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
