package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.info.KmpTargetsInfoTask
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The inert-module advisory (#71) wired into eager registration: when a module declared `supports {
 * }` but a registration pass ends with zero targets registered, the advisory names the consequence
 * — KGP's commonMain metadata compilation is doomed on a platform-less module — and the build-logic
 * gate (`registered().isEmpty()`), failing with the identical text under `kmptargets.strict=true`
 * through the same [warnOrFail] escalation point as every other advisory.
 *
 * Ordering doctrine under strict: the cause advisories (empty-overlap, android-without-AGP) are
 * emitted first and win the exception; inert is the consequence channel and the sole signal only
 * where no cause advisory covers the state (the explicitly-empty selection of issue #9).
 */
class InertModuleAdvisoryInProcessTest {

    @Test
    fun `given a disjoint selection when supports is declared then the inert advisory fires and nothing registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=wasmJs\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        assertEquals(emptySet(), registeredTargets(project))
        assertTrue(extension.inertWarned)
    }

    @Test
    fun `given supports never called when the project configures then no inert signal fires`(
        @TempDir dir: Path
    ) {
        // Explicit-selection doctrine: never declaring supports { } is the intentional way to
        // register nothing — register() never runs, so the advisory cannot fire, and the info
        // input reports false for the same reason (supportsDeclared gates the predicate).
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        assertFalse(extension.inertWarned)
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertFalse(task.inertModule.get())
    }

    @Test
    fun `given an explicitly empty selection when supports is declared then the inert advisory fires`(
        @TempDir dir: Path
    ) {
        // `jvm,-jvm` parses to the explicit "build nothing" selection (issue #9). Empty-overlap is
        // silent here by design; inert is the sole signal for the doomed metadata compilation.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,-jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        assertEquals(emptySet(), registeredTargets(project))
        assertTrue(extension.inertWarned)
    }

    @Test
    fun `given strict on and an explicitly empty selection when supports is declared then the build fails with the inert text`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm,-jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val ex = assertFailsWith<GradleException> { ext(project).supports { jvm } }
        // Verbatim equality also proves empty-overlap stayed silent: had it fired, its text would
        // have won the exception (it is emitted first).
        assertEquals(inertModuleWarning(project.path), ex.message)
    }

    @Test
    fun `given strict on and a disjoint selection when supports is declared then the build fails with the empty-overlap text`(
        @TempDir dir: Path
    ) {
        // Ordering: the cause advisory wins the strict exception; inert (the consequence) is
        // emitted after it and never reached once empty-overlap throws.
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=wasmJs\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        val ex = assertFailsWith<GradleException> { extension.supports { jvm } }
        assertEquals(
            emptyOverlapWarning(
                path = project.path,
                selection = KmpTargetSet.of(KmpTarget.Web.WasmJs),
                supported = KmpTargetSet.of(KmpTarget.Jvm.Desktop),
            ),
            ex.message,
        )
        assertFalse(extension.inertWarned)
    }

    @Test
    fun `given strict on when the inert advisory throws then it leaves no dedup bookkeeping behind`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm,-jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        assertFailsWith<GradleException> { extension.supports { jvm } }
        // Mirrors the host/deprecated/android doctrine: the flag is recorded AFTER warnOrFail, so a
        // strict failure does not mark the module as already-warned.
        assertFalse(extension.inertWarned)
    }

    @Test
    fun `given the advisory already fired when a second still-inert supports union runs then it does not repeat`(
        @TempDir dir: Path
    ) {
        // Strict makes "does not repeat" assertable: pre-seeding the dedup flag must leave the
        // second pass with nothing to throw (mirrors the android suite's pre-seed pattern). The
        // explicitly-empty selection keeps empty-overlap out of the way — inert is the only
        // advisory in play.
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm,-jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.inertWarned = true
        extension.supports { jvm }
        assertEquals(emptySet(), registeredTargets(project))
    }

    @Test
    fun `given an inert module when a later supports union registers a leaf then the module un-inerts`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { iosArm64 }
        assertTrue(extension.inertWarned)
        // Registration is one-way: the union registers jvm, registered() goes (permanently)
        // non-empty, and the predicate can never flag this module again.
        extension.supports { jvm }
        assertEquals(setOf("jvm"), registeredTargets(project))
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Desktop), extension.registered())
    }

    @Test
    fun `given a first supports pass that registers a leaf when a later disjoint union runs then inert never fires`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        extension.supports { iosArm64 }
        assertEquals(setOf("jvm"), registeredTargets(project))
        assertFalse(extension.inertWarned)
    }

    @Test
    fun `given an android-only overlap without AGP when supports is declared then both the android and inert advisories fire`(
        @TempDir dir: Path
    ) {
        // The #51 skip leaves the pass with zero registrations and empty-overlap silent (the
        // overlap is non-empty) — the state empty-overlap misses and inert covers.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=android\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { androidTarget }
        assertEquals(emptySet(), registeredTargets(project))
        assertTrue(extension.androidAgpWarned)
        assertTrue(extension.inertWarned)
    }

    @Test
    fun `given strict on and an android-only overlap without AGP when supports is declared then the build fails with the android text`(
        @TempDir dir: Path
    ) {
        // Cause wins again: android-without-AGP is emitted before inert.
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=android\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val ex = assertFailsWith<GradleException> { ext(project).supports { androidTarget } }
        assertEquals(androidWithoutAgpWarning(project.path), ex.message)
    }

    @Test
    fun `given supports declares an empty set when registration runs then both empty-overlap and inert fire`(
        @TempDir dir: Path
    ) {
        // Pins the declared-but-empty interaction: supportsProperty is present, the overlap with
        // the (default, non-empty) selection is empty — empty-overlap fires with its "supports []"
        // text AND inert flags the doomed metadata compilation.
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports(KmpTargetSet.empty)
        assertEquals(emptySet(), registeredTargets(project))
        assertTrue(extension.inertWarned)
    }

    @Test
    fun `given a host-impossible-only selection that still registers when supports is declared then the module is not inert`(
        @TempDir dir: Path
    ) {
        // Cross-host stability: registration is host-blind, so iosArm64 registers on macOS, Linux,
        // and Windows alike — a registered-but-uncompilable module is NOT inert on any host.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { iosArm64 }
        assertEquals(setOf("iosArm64"), registeredTargets(project))
        assertFalse(extension.inertWarned)
    }

    @Test
    fun `given a disjoint module when the inert info input is read then it is true`(
        @TempDir dir: Path
    ) {
        // Same decision source as the advisory (shouldWarnInertModule over registered()), realized
        // lazily at task-graph calculation.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=wasmJs\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm }
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertTrue(task.inertModule.get())
    }

    @Test
    fun `given an overlapping module when the inert info input is read then it is false`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm }
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertFalse(task.inertModule.get())
    }

    @Test
    fun `given no KGP applied when the inert info input is read then it is false`(
        @TempDir dir: Path
    ) {
        // Without KGP nothing registers — but no commonMain metadata compilation exists either, so
        // there is no trap to signal. The provider's KGP guard keeps the report quiet.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=wasmJs\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        ext(project).supports { jvm }
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertFalse(task.inertModule.get())
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
