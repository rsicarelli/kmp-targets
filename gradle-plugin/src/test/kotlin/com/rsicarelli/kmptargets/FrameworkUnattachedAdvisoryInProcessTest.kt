package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.doctor.KmpTargetsDoctorTask
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
 * The framework-unattached advisory (#107) wired into eager registration: when a module declares an
 * `appleFramework` and `supports { }`, but the selection attaches it to no Apple leaf in its `on`
 * scope, the framework never materializes and an Xcode build consuming it fails downstream. The
 * advisory names the module, the framework, and the gate, failing with the identical text under
 * `kmptargets.strict=true` through the same [warnOrFail] escalation as every other advisory.
 *
 * It fires at the end of every `register()` pass AND on the `appleFramework()` replay hook, so it
 * is order-immune (framework declared before or after `supports { }`). It is keyed off the
 * genuinely attached leaves rather than `registered(apple)`, so an `on` scope narrower than what
 * registered is flagged correctly. Last in the consequence channel: under strict, the cause
 * advisories and the more fundamental inert/JVM-less consequences win the exception.
 */
class FrameworkUnattachedAdvisoryInProcessTest {

    @Test
    fun `given a jvm-only selection and a declared framework when supports is declared then the advisory fires`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        extension.supports { jvm }
        assertEquals(setOf("jvm"), registeredTargets(project))
        assertTrue(extension.frameworkAttachedLeaves().isEmpty())
        assertTrue(extension.frameworkUnattachedWarned)
    }

    @Test
    fun `given a framework scoped to appleMobile but only macOS registered when supports is declared then the advisory fires despite apple leaves registering`(
        @TempDir dir: Path
    ) {
        // The on-scoped gate (#107): registered(apple) is non-empty (macOS registered), yet the
        // framework — scoped to appleMobile — attached to nothing, so it still never builds. This
        // is
        // exactly the case the literal `registered(apple).isEmpty()` gate would miss.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=macosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.appleFramework("KotlinShared", on = KmpTargetSet.appleMobile)
        extension.supports(KmpTargetSet.of(KmpTarget.Native.Apple.Macos.Arm64))
        assertEquals(setOf("macosArm64"), registeredTargets(project))
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Macos.Arm64), extension.registered())
        assertTrue(extension.frameworkAttachedLeaves().isEmpty())
        assertTrue(extension.frameworkUnattachedWarned)
    }

    @Test
    fun `given an apple leaf in the framework scope registers when supports is declared then the advisory stays silent`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        extension.supports(KmpTargetSet.appleMobile)
        assertEquals(setOf("iosArm64"), registeredTargets(project))
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
            extension.frameworkAttachedLeaves(),
        )
        assertFalse(extension.frameworkUnattachedWarned)
    }

    @Test
    fun `given a framework declared AFTER a jvm-only supports when declared then the replay hook fires the advisory`(
        @TempDir dir: Path
    ) {
        // The order that needs the dedicated hook: supports { } already ran register() (no
        // framework
        // then), and declaring the framework no longer re-runs register() — only its replay hook
        // gives the advisory a chance to fire.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        assertFalse(extension.frameworkUnattachedWarned)
        extension.appleFramework("KotlinShared")
        assertTrue(extension.frameworkUnattachedWarned)
    }

    @Test
    fun `given a framework declared on a jvm-only module when a later supports union attaches an apple leaf then the state resolves`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64,jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        extension.supports { jvm }
        assertTrue(extension.frameworkUnattachedWarned)
        // Registration is one-way: the union registers iosArm64, the framework attaches, and the
        // attached set goes (permanently) non-empty — the predicate can never flag this module
        // again.
        extension.supports(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64))
        assertEquals(setOf("iosArm64", "jvm"), registeredTargets(project))
        assertEquals(
            KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64),
            extension.frameworkAttachedLeaves(),
        )
    }

    @Test
    fun `given a framework but supports never called when the project configures then no advisory fires but the doctor renders the facts`(
        @TempDir dir: Path
    ) {
        // Explicit-selection doctrine: no supports { } means register() never runs and the
        // predicate
        // is gated off, so there is no advisory — but the declared-but-unattached state is still
        // worth surfacing, so the doctor renders it (attached: empty, no finding).
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        assertFalse(extension.frameworkUnattachedWarned)
        val doctor = doctor(project)
        assertTrue(doctor.frameworkDeclared.get())
        assertEquals("KotlinShared", doctor.frameworkBaseName.get())
        assertEquals(emptyList(), doctor.frameworkAttachedIds.get())
        assertFalse(doctor.frameworkUnattached.get())
    }

    @Test
    fun `given no KGP applied when a framework and supports are declared then no advisory fires`(
        @TempDir dir: Path
    ) {
        // Without KGP nothing registers and onRegistered never fires, so the framework attaches to
        // nothing — but there is no link/embed task to break either. The replay hook's KGP guard
        // keeps it quiet, consistent with the sibling advisories.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        extension.supports(KmpTargetSet.appleMobile)
        assertFalse(extension.frameworkUnattachedWarned)
    }

    @Test
    fun `given strict on and a framework declared before a jvm-only supports when supports runs then the build fails with the framework text`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        val ex = assertFailsWith<GradleException> { extension.supports { jvm } }
        assertEquals(frameworkUnattachedWarning(project.path, "KotlinShared"), ex.message)
    }

    @Test
    fun `given strict on and a framework declared after a jvm-only supports when declared then the replay hook fails with the framework text`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        val ex = assertFailsWith<GradleException> { extension.appleFramework("KotlinShared") }
        assertEquals(frameworkUnattachedWarning(project.path, "KotlinShared"), ex.message)
    }

    @Test
    fun `given strict on when the framework advisory throws then it leaves no dedup bookkeeping behind`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        assertFailsWith<GradleException> { extension.supports { jvm } }
        // Mirrors the sibling advisories: the flag is recorded AFTER warnOrFail, so a strict
        // failure
        // does not mark the module as already-warned.
        assertFalse(extension.frameworkUnattachedWarned)
    }

    @Test
    fun `given strict on and an explicitly empty selection with a declared framework when supports is declared then inert wins over the framework advisory`(
        @TempDir dir: Path
    ) {
        // Ordering proof: an inert module (registered nothing) with a declared framework trips BOTH
        // inert and framework-unattached. Inert is emitted first (the more fundamental
        // consequence),
        // so its text wins the strict exception; the framework advisory — last — is never reached.
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=jvm,-jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        val ex = assertFailsWith<GradleException> { extension.supports(KmpTargetSet.appleMobile) }
        assertEquals(inertModuleWarning(project.path), ex.message)
        assertFalse(extension.frameworkUnattachedWarned)
    }

    @Test
    fun `given an unattached framework when the doctor finding input is read then it is true`(
        @TempDir dir: Path
    ) {
        // Same decision source as the advisory (shouldWarnFrameworkUnattached over the attached
        // set),
        // realized lazily at task-graph calculation — so doctor's finding cannot drift.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        extension.supports { jvm }
        assertTrue(doctor(project).frameworkUnattached.get())
    }

    private fun newProjectWithKgp(dir: Path): Project {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        return project
    }

    private fun ext(project: Project): KmpTargetsExtension =
        project.extensions.getByType(KmpTargetsExtension::class.java)

    private fun doctor(project: Project): KmpTargetsDoctorTask =
        project.tasks.getByName("kmpTargetsDoctor") as KmpTargetsDoctorTask

    private fun registeredTargets(project: Project): Set<String> =
        project.extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .names
            .filter { it != "metadata" }
            .toSet()
}
