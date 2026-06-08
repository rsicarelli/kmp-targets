package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTargetSet
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
 * Proves the build-logic gate the user guide blesses for the commonMain-KSP-needs-a-native-target
 * trap (#73): a module whose KSP processor generates into commonMain via the native metadata route
 * skips generation under a native-less selection, so build-logic gates on
 * `kmpTargets.registered(KmpTargetSet.native).isEmpty()`. There is intentionally **no plugin
 * advisory** here — the trigger has a conjunct the plugin cannot observe ("does this module run a
 * commonMain metadata-route processor?"), so the plugin owns only the selection-awareness primitive
 * ([KmpTargetsExtension.registered]) and the gate stays in build-logic. These tests pin that the
 * primitive answers the gate truthfully.
 *
 * Registration is host-blind, so `iosArm64` registers on Linux CI too; nothing here compiles, so
 * the native leaf is cross-host safe.
 */
class CommonMainKspGateTest {

    @Test
    fun `given a jvm-only selection when supports is declared then the native gate is empty`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm }
        // The recipe's gate: registered(native).isEmpty() == true → build-logic warns and
        // disables the doomed codegen compilations.
        assertTrue(gate(project))
    }

    @Test
    fun `given a native leaf in the selection when supports is declared then the native gate is non-empty`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { iosArm64 }
        // A native leaf registered → the metadata-route codegen runs; the gate stays silent.
        assertEquals(setOf("iosArm64"), registeredTargets(project))
        assertFalse(gate(project))
    }

    @Test
    fun `given a jvm and web selection when supports is declared then the native gate is still empty`(
        @TempDir dir: Path
    ) {
        // The boundary that separates #73 from the bare metadata compilation: jvm+web shares a real
        // commonMain (see CommonMainMetadataCompilationInvariantTest) yet registers no native leaf
        // —
        // so the native-route processor's codegen is still skipped and the gate must fire.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,js\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm + js }
        assertEquals(setOf("jvm", "js"), registeredTargets(project))
        assertTrue(gate(project))
    }

    @Test
    fun `given a jvm selection when a later union registers a native leaf then the native gate resolves`(
        @TempDir dir: Path
    ) {
        // Registration is one-way: a later supports { } union that adds a native leaf flips the
        // gate
        // from empty to non-empty permanently — the doomed state resolves itself.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        assertTrue(gate(project), "gate fires while only jvm is registered")
        extension.supports { jvm + iosArm64 }
        assertFalse(gate(project), "gate resolves once the native leaf registers")
    }

    /** The exact predicate the user-guide recipe gates on. */
    private fun gate(project: Project): Boolean =
        ext(project).registered(KmpTargetSet.native).isEmpty()

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
