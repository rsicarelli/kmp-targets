package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.info.KmpTargetsInfoTask
import com.rsicarelli.kmptargets.model.KmpTarget
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
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
 * The android-without-AGP advisory (#51) wired into eager registration: when `androidTarget` is in
 * `selection ∩ supported` but no Android Gradle plugin is applied, the registration loop SKIPS the
 * leaf (KGP's `androidTarget()` is a FATAL diagnostic without AGP — pinned below) and the advisory
 * names the module and the fix — failing with the identical text under `kmptargets.strict=true`
 * through the same [warnOrFail] escalation point as every other advisory.
 *
 * No AGP is on this test classpath, which is exactly the condition under test. The complementary
 * AGP-applied path ("no advisory, android registers") cannot run in-process: faking the
 * `com.android.library` id makes KGP's eager `plugins.withId` wiring cast a real AGP
 * `BaseExtension` and crash, and a real AGP is incompatible with this Gradle/KGP test matrix. That
 * path is covered at the pure policy seam ([AndroidAgpAdvisoryTest]: `androidPluginApplied = true`
 * never flags), mirroring how `EnforceHostCompatibilityTest` simulates foreign hosts.
 */
class AndroidAgpAdvisoryInProcessTest {

    private val android = KmpTarget.Jvm.Android

    @Test
    fun `given a KGP project without any android plugin when androidTarget is called directly then KGP fails with its missing-AGP diagnostic`(
        @TempDir dir: Path
    ) {
        // Pins the observed KGP 2.3.21 behavior the guard exists for: androidTarget() without AGP
        // reports the FATAL AndroidGradlePluginIsMissing diagnostic, thrown immediately. If a
        // future KGP turns this into a no-op or warning, this red flags the guard for re-review.
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val ex = assertFails { kotlin.androidTarget() }
        assertContains(ex.message.orEmpty(), "Android Gradle Plugin")
    }

    @Test
    fun `given android selected and supported but no android plugin when supports is declared then android is skipped and the build succeeds`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=android,jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { androidTarget + jvm }
        // The skip is the advisory's whole point: without it, registration dies mid-loop on KGP's
        // raw FATAL. Everything else in the active set registers exactly as selected.
        assertEquals(setOf("jvm"), registeredTargets(project))
        assertTrue(extension.androidAgpWarned)
    }

    @Test
    fun `given strict on and android selected and supported but no android plugin when supports is declared then the build fails with the advisory text`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=android,jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val ex = assertFailsWith<GradleException> { ext(project).supports { androidTarget + jvm } }
        assertEquals(androidWithoutAgpWarning(project.path), ex.message)
    }

    @Test
    fun `given android selected but not supported when supports is declared then no advisory fires`(
        @TempDir dir: Path
    ) {
        // Keyed off the ACTIVE set: android the module cannot build was never going to register,
        // so there is nothing to flag.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=android,jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        assertFalse(extension.androidAgpWarned)
    }

    @Test
    fun `given android supported but not selected when supports is declared then no advisory fires`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { androidTarget + jvm }
        assertFalse(extension.androidAgpWarned)
    }

    @Test
    fun `given the advisory already fired when a second supports union re-registers then it does not repeat`(
        @TempDir dir: Path
    ) {
        // Strict makes "does not repeat" assertable: pre-seeding the dedup flag must leave the
        // second pass with nothing to throw (mirrors the deprecation suite's hostWarned pre-seed).
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=android,jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.androidAgpWarned = true
        extension.supports { androidTarget + jvm }
        assertEquals(setOf("jvm"), registeredTargets(project))
    }

    @Test
    fun `given the advisory fired once when supports is called again without AGP then the flag stays set and nothing fails`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=android,jvm,js\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { androidTarget + jvm }
        assertTrue(extension.androidAgpWarned)
        // The union re-runs registration; android is still active and still AGP-less, but the
        // module was already warned — the pass stays quiet and registers only the fresh leaf.
        extension.supports { js }
        assertTrue(extension.androidAgpWarned)
        assertEquals(setOf("jvm", "js"), registeredTargets(project))
    }

    @Test
    fun `given android skipped when registration completes then android stays out of the registered bookkeeping`(
        @TempDir dir: Path
    ) {
        // NOT recording the skipped leaf is what lets a later supports{} pass — after AGP is
        // applied — register it: the loop only registers `active - registered`.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=android,jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { androidTarget + jvm }
        assertFalse(android in extension.registeredLeaves)
    }

    @Test
    fun `given android active without an android plugin when the info annotation input is read then it is true`(
        @TempDir dir: Path
    ) {
        // Same decision source as the advisory (shouldWarnAndroidWithoutAgp), realized lazily at
        // task-graph calculation — so the report annotates the registered section with
        // `androidTarget (skipped: no Android plugin applied)` exactly when the advisory fired.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=android,jvm\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { androidTarget + jvm }
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertTrue(task.androidWithoutAgp.get())
    }

    @Test
    fun `given android not selected when the info annotation input is read then it is false`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { androidTarget + jvm }
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertFalse(task.androidWithoutAgp.get())
    }

    @Test
    fun `given strict on when the advisory throws then it leaves no dedup bookkeeping behind`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=android,jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        assertFailsWith<GradleException> { extension.supports { androidTarget + jvm } }
        // Mirrors the host/deprecated doctrine: the flag is recorded AFTER warnOrFail, so a strict
        // failure does not mark the module as already-warned.
        assertFalse(extension.androidAgpWarned)
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
