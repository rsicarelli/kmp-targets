package com.rsicarelli.kmptargets

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Pins the build-logic gate the user guide blesses for the commonMain-KSP trap (#73): a processor
 * that generates into `commonMain` runs on the commonMain metadata compilation
 * (`kspCommonMainKotlinMetadata`), which — like KGP's `compileCommonMainKotlinMetadata`
 * ([CommonMainMetadataCompilationInvariantTest]) — exists only when **≥2 platform targets share
 * commonMain**. A **single-target** lane has no such route, so the codegen is skipped. The earlier
 * version of this test gated on `registered(native).isEmpty()`; that was wrong (and actively
 * harmful — it disabled compilation on working `jvm`/`android,jvm` lanes). The ktorfit sample
 * measured KSP's actual route: native is neither necessary (`jvm,js` gets it) nor sufficient
 * (`iosArm64` alone does not), so the correct gate is **target count**, `registered().size < 2`.
 *
 * There is intentionally no plugin advisory for this — only a doctor finding (`single-target KSP`,
 * see [SingleTargetKspFindingTest]) — because the trigger's other conjunct ("does this module run a
 * commonMain metadata-route processor?") is not observable. These tests pin that the primitive
 * answers the corrected gate truthfully.
 *
 * Registration is host-blind, so `iosArm64` registers on Linux CI too; nothing here compiles, so
 * the native leaf is cross-host safe.
 */
class CommonMainKspSingleTargetGateTest {

    @Test
    fun `given a jvm-only selection when supports is declared then the single-target gate fires`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm }
        // One target → no shared commonMain metadata route → a commonMain processor is skipped.
        assertTrue(gate(project))
    }

    @Test
    fun `given a single native leaf when supports is declared then the single-target gate still fires`(
        @TempDir dir: Path
    ) {
        // Native presence is not the trigger: a lone native target has no shared commonMain either.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { iosArm64 }
        assertEquals(setOf("iosArm64"), registeredTargets(project))
        assertTrue(gate(project))
    }

    @Test
    fun `given a jvm and web selection when supports is declared then the gate does not fire despite no native`(
        @TempDir dir: Path
    ) {
        // The correction that matters: two NON-native targets share a real commonMain, so the
        // metadata route exists and codegen runs — the gate must stay silent even with zero native.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,js\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm + js }
        assertEquals(setOf("jvm", "js"), registeredTargets(project))
        assertFalse(gate(project))
    }

    @Test
    fun `given a jvm and native selection when supports is declared then the gate does not fire`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,iosArm64\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm + iosArm64 }
        assertEquals(setOf("jvm", "iosArm64"), registeredTargets(project))
        assertFalse(gate(project))
    }

    @Test
    fun `given a single target when a later union adds a second target then the gate resolves`(
        @TempDir dir: Path
    ) {
        // Registration is one-way: a later supports { } union that adds a second target flips the
        // gate from firing to silent permanently — the doomed single-target state resolves itself.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        assertTrue(gate(project), "gate fires while only jvm is registered")
        extension.supports { jvm + iosArm64 }
        assertFalse(gate(project), "gate resolves once a second target registers")
    }

    /** The exact predicate the user-guide recipe gates on: fewer than two registered targets. */
    private fun gate(project: Project): Boolean = ext(project).registered().size < 2

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
