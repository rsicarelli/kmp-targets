package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Strict mode (`kmptargets.strict`): the opt-in that promotes the empty-overlap advisory to a
 * `GradleException` at the eager registration point. KGP is applied BEFORE `supports { … }` is
 * called directly on the extension, so the failure propagates unwrapped from the `supports` call
 * and the message can be asserted for verbatim equality with [emptyOverlapWarning]. Default OFF —
 * behavior without the flag must be byte-for-byte the advisory-only path.
 *
 * The host-impossible strict path is covered deterministically through the pure seam in
 * `host/EnforceHostCompatibilityTest` (a real host that can compile everything would never trigger
 * it here).
 */
class KmpTargetsStrictModeTest {

    @Test
    fun `given strict off by default and a disjoint selection when supports is declared then configuration succeeds and registers nothing`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=wasmJs\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm }
        assertEquals(emptySet(), registeredTargets(project))
    }

    @Test
    fun `given strict on and a disjoint selection when supports is declared then the build fails with the empty-overlap text`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=wasmJs\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val ex = assertFailsWith<GradleException> { ext(project).supports { jvm } }
        assertEquals(
            emptyOverlapWarning(
                path = project.path,
                selection = KmpTargetSet.of(KmpTarget.Web.WasmJs),
                supported = KmpTargetSet.of(KmpTarget.Jvm.Desktop),
            ),
            ex.message,
        )
    }

    @Test
    fun `given strict on and an overlapping selection when supports is declared then configuration succeeds and registers the intersection`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm,wasmJs\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm }
        assertEquals(setOf("jvm"), registeredTargets(project))
    }

    @Test
    fun `given a non-boolean strict value and a disjoint selection when supports is declared then strict is treated as off`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=wasmJs\nkmptargets.strict=yes\n")
        val project = newProjectWithKgp(dir)
        // `yes` is not a strict boolean → unset → OFF: advisory only, no failure.
        ext(project).supports { jvm }
        assertEquals(emptySet(), registeredTargets(project))
    }

    @Test
    fun `given strict false in gradle properties and true in local properties when disjoint then the gradle property wins and it does not fail`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=wasmJs\nkmptargets.strict=false\n")
        dir.resolve("local.properties").writeText("kmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm }
        assertEquals(emptySet(), registeredTargets(project))
    }

    @Test
    fun `given strict enabled via the committed config file when disjoint then the build fails`(
        @TempDir dir: Path
    ) {
        // Also proves kmptargets.strict is a known key for the dedicated files' fail-loud
        // validation — an unknown key would already fail at apply with a did-you-mean error.
        dir.resolve("kmp-targets.properties")
            .writeText("kmptargets.targets=wasmJs\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        assertFailsWith<GradleException> { ext(project).supports { jvm } }
    }

    @Test
    fun `given strict enabled via the cli property when disjoint then the build fails`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=wasmJs\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.gradle.startParameter.projectProperties = mapOf("kmptargets.strict" to "true")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        assertFailsWith<GradleException> { ext(project).supports { jvm } }
    }

    private fun newProjectWithKgp(dir: Path): Project {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        return project
    }

    private fun ext(project: Project): KmpTargetsExtension =
        project.extensions.getByType(KmpTargetsExtension::class.java)

    private fun registeredTargets(project: Project): Set<String> =
        project.extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .names
            .filter { it != "metadata" }
            .toSet()
}
