package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.doctor.KmpTargetsDoctorTask
import com.rsicarelli.kmptargets.info.KmpTargetsInfoTask
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * `kmptargets.framework.buildTypes` (#108) wired end-to-end through the selection-sources chain:
 * the effective build types are `property ∩ appleFramework(buildTypes = …)`, the lane only ever
 * narrows the declaration (and so drives both the per-buildType link tasks and the XCFramework
 * assemble variants), an absent property keeps the declared value, and a disjoint pair links
 * nothing and fires the build-types-disjoint advisory (strict fails) — mutually exclusive with the
 * unattached advisory (#107). `kmpTargetsInfo` surfaces the effective set and the winning origin
 * layer.
 */
class FrameworkBuildTypesInProcessTest {

    private val debugOnly = listOf(NativeBuildType.DEBUG)

    @Test
    fun `given the debug property and a default framework when declared then only the debug framework binary links`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, targets = "iosArm64", buildTypes = "debug")
        ext(project).appleFramework("KotlinShared")
        ext(project).supports(KmpTargetSet.appleMobile)
        assertEquals(
            listOf(NativeBuildType.DEBUG),
            project.frameworksByTarget().getValue("iosArm64").map { it.buildType },
        )
    }

    @Test
    fun `given no build-types property when a default framework is declared then the declared build types win`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, targets = "iosArm64")
        ext(project).appleFramework("KotlinShared")
        ext(project).supports(KmpTargetSet.appleMobile)
        assertEquals(
            setOf(NativeBuildType.DEBUG, NativeBuildType.RELEASE),
            project.frameworksByTarget().getValue("iosArm64").map { it.buildType }.toSet(),
        )
    }

    @Test
    fun `given a property that supersets the declaration when declared then intersection never widens past the declaration`(
        @TempDir dir: Path
    ) {
        // property = {DEBUG, RELEASE}, declaration = {DEBUG}: the property can only narrow, so the
        // effective set stays {DEBUG} (it can never link a build type the module did not declare).
        val project = projectWith(dir, targets = "iosArm64", buildTypes = "debug,release")
        ext(project).appleFramework("KotlinShared", buildTypes = debugOnly)
        ext(project).supports(KmpTargetSet.appleMobile)
        assertEquals(
            listOf(NativeBuildType.DEBUG),
            project.frameworksByTarget().getValue("iosArm64").map { it.buildType },
        )
    }

    @Test
    fun `given the debug property and xcframework when declared then only the debug assemble variant exists`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, targets = "iosArm64,iosSimulatorArm64", buildTypes = "debug")
        ext(project).appleFramework("KotlinShared", xcframework = true)
        ext(project).supports(KmpTargetSet.appleMobile)
        val tasks = project.xcframeworkTaskNames()
        assertTrue("assembleKotlinSharedDebugXCFramework" in tasks, tasks.toString())
        assertTrue("assembleKotlinSharedReleaseXCFramework" !in tasks, tasks.toString())
    }

    @Test
    fun `given a property disjoint from the declaration when declared then nothing links and the disjoint advisory fires while unattached stays silent`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, targets = "iosArm64", buildTypes = "release")
        val extension = ext(project)
        extension.appleFramework("KotlinShared", buildTypes = debugOnly)
        extension.supports(KmpTargetSet.appleMobile)
        // The Apple leaf registered and the framework attached to it, but the build types do not
        // overlap, so no Framework binary links at all.
        assertTrue(project.frameworksByTarget().isEmpty(), project.frameworksByTarget().toString())
        assertTrue(extension.frameworkAttachedLeaves().isNotEmpty())
        assertTrue(extension.frameworkBuildTypesDisjointWarned)
        assertFalse(extension.frameworkUnattachedWarned)
    }

    @Test
    fun `given a disjoint property when the doctor finding input is read then it is true`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, targets = "iosArm64", buildTypes = "release")
        ext(project).appleFramework("KotlinShared", buildTypes = debugOnly)
        ext(project).supports(KmpTargetSet.appleMobile)
        assertTrue(doctor(project).frameworkBuildTypesDisjoint.get())
    }

    @Test
    fun `given strict on and a disjoint property when supports runs then the build fails with the disjoint text and leaves no bookkeeping`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, targets = "iosArm64", buildTypes = "release", strict = true)
        val extension = ext(project)
        // Pre-mark the active Apple leaf as host-warned so the host-impossible advisory — a cause,
        // emitted before the framework consequence advisories — does not pre-empt the strict
        // failure
        // on a non-Apple CI host (iosArm64 cannot compile on LINUX). This isolates the disjoint
        // advisory's own strict escalation; the host advisory dedups via this exact set.
        extension.hostWarned += KmpTarget.Native.Apple.Ios.Arm64
        extension.appleFramework("KotlinShared", buildTypes = debugOnly)
        val ex = assertFailsWith<GradleException> { extension.supports(KmpTargetSet.appleMobile) }
        assertEquals(
            frameworkBuildTypesDisjointWarning(
                project.path,
                "KotlinShared",
                setOf(NativeBuildType.RELEASE),
                setOf(NativeBuildType.DEBUG),
            ),
            ex.message,
        )
        // Recorded AFTER warnOrFail, so a strict failure leaves the module un-warned (sibling
        // rule).
        assertFalse(extension.frameworkBuildTypesDisjointWarned)
    }

    @Test
    fun `given a jvm-only selection and the debug property when declared then unattached fires and disjoint stays silent`(
        @TempDir dir: Path
    ) {
        // No Apple leaf attaches, so there is no framework to link — the unattached advisory (#107)
        // owns this state; the disjoint advisory is gated on ≥1 attached leaf and stays silent.
        val project = projectWith(dir, targets = "jvm", buildTypes = "debug")
        val extension = ext(project)
        extension.appleFramework("KotlinShared")
        extension.supports { jvm }
        assertTrue(extension.frameworkUnattachedWarned)
        assertFalse(extension.frameworkBuildTypesDisjointWarned)
    }

    @Test
    fun `given a disjoint framework declared AFTER supports when declared then the replay hook fires the disjoint advisory`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, targets = "iosArm64", buildTypes = "release")
        val extension = ext(project)
        extension.supports(KmpTargetSet.appleMobile)
        assertFalse(extension.frameworkBuildTypesDisjointWarned)
        extension.appleFramework("KotlinShared", buildTypes = debugOnly)
        assertTrue(extension.frameworkBuildTypesDisjointWarned)
    }

    @Test
    fun `given the debug property when the info inputs are read then they show the effective set the declared set and the origin layer`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, targets = "iosArm64", buildTypes = "debug")
        ext(project).appleFramework("KotlinShared")
        ext(project).supports(KmpTargetSet.appleMobile)
        val info = info(project)
        assertEquals(listOf("DEBUG"), info.frameworkBuildTypes.get())
        assertEquals(listOf("DEBUG", "RELEASE"), info.frameworkBuildTypesDeclared.get())
        assertContains(info.frameworkBuildTypesOrigin.get(), "gradle.properties")
    }

    @Test
    fun `given a typo of the framework build-types key in the committed file when applied then it fails with a did-you-mean`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.properties").writeText("kmptargets.framework.buildType=debug\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        val ex =
            assertFailsWith<GradleException> {
                project.pluginManager.apply("com.rsicarelli.kmptargets")
            }
        // pluginManager.apply wraps the plugin's GradleException in a PluginApplicationException,
        // so
        // the validateKnownKeys diagnostic is on the cause chain, not the top-level message.
        val messages =
            generateSequence(ex as Throwable?) { it.cause }
                .mapNotNull { it.message }
                .joinToString("\n")
        assertContains(messages, "kmptargets.framework.buildType")
        assertContains(messages, "did you mean 'kmptargets.framework.buildTypes'?")
    }

    private fun projectWith(
        dir: Path,
        targets: String,
        buildTypes: String? = null,
        strict: Boolean = false,
    ): Project {
        dir.resolve("gradle.properties")
            .writeText(
                buildString {
                    appendLine("kmptargets.targets=$targets")
                    if (buildTypes != null)
                        appendLine("kmptargets.framework.buildTypes=$buildTypes")
                    if (strict) appendLine("kmptargets.strict=true")
                }
            )
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        return project
    }

    private fun ext(project: Project): KmpTargetsExtension =
        project.extensions.getByType(KmpTargetsExtension::class.java)

    private fun info(project: Project): KmpTargetsInfoTask =
        project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask

    private fun doctor(project: Project): KmpTargetsDoctorTask =
        project.tasks.getByName("kmpTargetsDoctor") as KmpTargetsDoctorTask

    /** Every [Framework] binary the plugin attached, grouped by the KGP target name. */
    private fun Project.frameworksByTarget(): Map<String, List<Framework>> =
        extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .withType(KotlinNativeTarget::class.java)
            .associate { it.targetName to it.binaries.withType(Framework::class.java).toList() }
            .filterValues { it.isNotEmpty() }

    private fun Project.xcframeworkTaskNames(): Set<String> =
        tasks.names.filter { it.endsWith("XCFramework") }.toSet()
}
