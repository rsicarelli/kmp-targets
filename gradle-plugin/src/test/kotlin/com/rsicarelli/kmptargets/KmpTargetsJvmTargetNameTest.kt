package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The per-module JVM target rename (issue #49): codebases that predate the default-hierarchy era
 * register their JVM target under a custom name (canonically `jvm("desktop")`) and carry
 * load-bearing `src/desktopMain` dirs and `-desktop` artifact suffixes.
 * [KmpTargetsExtension .targetName] lets the jvm leaf register under that name while the selection
 * token stays `jvm`.
 */
class KmpTargetsJvmTargetNameTest {

    @Test
    fun `given targetName jvm desktop when jvm is supported then a target named desktop registers and jvm does not`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "jvm")
        project.kmpTargets().targetName(KmpTargetsDsl.jvm, "desktop")
        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop))
        val targets = project.kotlinTargetNames()
        assertTrue("desktop" in targets, targets.toString())
        assertFalse("jvm" in targets, "renamed target must not also register as jvm: $targets")
    }

    @Test
    fun `given the raw jvmTargetName property when jvm is supported then the renamed target registers`(
        @TempDir dir: Path
    ) {
        // The Property itself is public API for build-logic — it must work without the sugar.
        val project = projectWithSelection(dir, "jvm")
        project.kmpTargets().jvmTargetName.set("desktop")
        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop))
        val targets = project.kotlinTargetNames()
        assertTrue("desktop" in targets, targets.toString())
        assertFalse("jvm" in targets, targets.toString())
    }

    @Test
    fun `given targetName jvm desktop when selection uses the jvm token then the renamed target still registers`(
        @TempDir dir: Path
    ) {
        // The rename changes only the registered Gradle name — never the selection vocabulary.
        val project = projectWithSelection(dir, "jvm,iosArm64")
        project.kmpTargets().targetName(KmpTargetsDsl.jvm, "desktop")
        project.kmpTargets().supports(KmpTargetSet.all)
        val targets = project.kotlinTargetNames()
        assertTrue("desktop" in targets, targets.toString())
        assertTrue("iosArm64" in targets, targets.toString())
    }

    @Test
    fun `given a non-jvm leaf when targetName is called then it fails naming the jvm-only contract`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "jvm")
        val ex =
            assertFailsWith<GradleException> {
                project.kmpTargets().targetName(KmpTargetsDsl.iosArm64, "device")
            }
        assertTrue(
            ex.message!!.contains("only the jvm target is renamable"),
            "expected jvm-only contract in message, got: ${ex.message}",
        )
    }

    @Test
    fun `given a preset instead of a leaf when targetName is called then it fails naming the jvm-only contract`(
        @TempDir dir: Path
    ) {
        val project = projectWithSelection(dir, "jvm")
        val ex =
            assertFailsWith<GradleException> {
                project.kmpTargets().targetName(KmpTargetsDsl.mobile, "desktop")
            }
        assertTrue(
            ex.message!!.contains("only the jvm target is renamable"),
            "expected jvm-only contract in message, got: ${ex.message}",
        )
    }

    @Test
    fun `given a blank name when targetName is called then it fails`(@TempDir dir: Path) {
        val project = projectWithSelection(dir, "jvm")
        val ex =
            assertFailsWith<GradleException> {
                project.kmpTargets().targetName(KmpTargetsDsl.jvm, "  ")
            }
        assertTrue(
            ex.message!!.contains("must not be blank"),
            "expected blank-name contract in message, got: ${ex.message}",
        )
    }

    @Test
    fun `given supports already registered jvm when targetName is called then it fails fast about ordering`(
        @TempDir dir: Path
    ) {
        // Registration is eager and one-way: once the jvm leaf registered under its default name,
        // a later rename can never apply — silently ignoring it would be a lie.
        val project = projectWithSelection(dir, "jvm")
        project.kmpTargets().supports(KmpTargetSet.of(KmpTarget.Jvm.Desktop))
        val ex =
            assertFailsWith<GradleException> {
                project.kmpTargets().targetName(KmpTargetsDsl.jvm, "desktop")
            }
        assertTrue(
            ex.message!!.contains("before supports"),
            "expected set-before-supports contract in message, got: ${ex.message}",
        )
    }

    @Test
    fun `given renamed jvm and two ios leaves when the minimal template applies then desktopMain depends on commonMain`(
        @TempDir dir: Path
    ) {
        // KGP's hierarchy `withJvm()` matches by platform type, not target name — the renamed
        // target must attach exactly as a plain jvm would.
        val project = projectWithSelection(dir, "jvm,iosArm64,iosSimulatorArm64")
        project.kmpTargets().targetName(KmpTargetsDsl.jvm, "desktop")
        project.kmpTargets().supports(KmpTargetSet.all)
        (project as ProjectInternal).evaluate()
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val sourceSets = kotlin.sourceSets.names.toSet()
        assertTrue("desktopMain" in sourceSets, sourceSets.toString())
        assertFalse("jvmMain" in sourceSets, sourceSets.toString())
        assertTrue("iosMain" in sourceSets, sourceSets.toString())
        val dependsOn = kotlin.sourceSets.getByName("desktopMain").dependsOn.map { it.name }
        assertTrue("commonMain" in dependsOn, dependsOn.toString())
    }

    private fun projectWithSelection(dir: Path, selection: String): Project {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=$selection\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        return project
    }

    private fun Project.kmpTargets(): KmpTargetsExtension =
        extensions.getByType(KmpTargetsExtension::class.java)

    private fun Project.kotlinTargetNames(): Set<String> =
        extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .targets
            .names
            .filter { it != "metadata" }
            .toSet()
}
