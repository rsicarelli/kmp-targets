package com.rsicarelli.kmptargets.hierarchy

import com.rsicarelli.kmptargets.KmpTargetsExtension
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Proves the hierarchy template against the *real* Kotlin Multiplatform plugin applied in-process.
 *
 * `ProjectBuilder` + [ProjectInternal.evaluate] pumps KGP's lifecycle far enough that hierarchy
 * source sets actually materialize (verified: KGP's default template creates
 * `appleMain`/`nativeMain` for an iOS-only module). So these tests assert on the concrete
 * `kotlin.sourceSets` names — the load-bearing "does the minimal template actually apply, and does
 * it suppress KGP's default" proof.
 */
class HierarchyTemplateInProcessTest {

    @Test
    fun `given iOS only when applied then iosMain materializes and appleMain nativeMain are suppressed`(
        @TempDir dir: Path
    ) {
        val sourceSets = sourceSetsOf(dir, "iosArm64,iosX64,iosSimulatorArm64")
        assertTrue("iosMain" in sourceSets, sourceSets.toString())
        assertFalse("appleMain" in sourceSets, "default not suppressed: $sourceSets")
        assertFalse("nativeMain" in sourceSets, "default not suppressed: $sourceSets")
    }

    @Test
    fun `given iOS and macOS when applied then appleMain materializes but nativeMain is suppressed`(
        @TempDir dir: Path
    ) {
        val sourceSets = sourceSetsOf(dir, "iosArm64,iosX64,iosSimulatorArm64,macosArm64,macosX64")
        assertTrue("appleMain" in sourceSets, sourceSets.toString())
        assertTrue("iosMain" in sourceSets, sourceSets.toString())
        assertTrue("macosMain" in sourceSets, sourceSets.toString())
        assertFalse("nativeMain" in sourceSets, "nativeMain should collapse: $sourceSets")
    }

    @Test
    fun `given jvm only when applied then no native intermediate source sets exist`(
        @TempDir dir: Path
    ) {
        val sourceSets = sourceSetsOf(dir, "jvm")
        assertFalse("nativeMain" in sourceSets, sourceSets.toString())
        assertFalse("appleMain" in sourceSets, sourceSets.toString())
    }

    @Test
    fun `given hierarchyTemplate disabled via extension and iOS only when applied then KGP default applies`(
        @TempDir dir: Path
    ) {
        val sourceSets =
            sourceSetsOf(dir, "iosArm64,iosX64,iosSimulatorArm64") {
                it.extensions
                    .getByType(KmpTargetsExtension::class.java)
                    .hierarchyTemplate
                    .set(false)
            }
        assertTrue(
            "appleMain" in sourceSets,
            "opt-out should fall back to KGP default: $sourceSets",
        )
        assertTrue("nativeMain" in sourceSets, sourceSets.toString())
    }

    @Test
    fun `given global property false and iOS only when applied then KGP default applies`(
        @TempDir dir: Path
    ) {
        val sourceSets =
            sourceSetsOf(
                dir,
                "iosArm64,iosX64,iosSimulatorArm64",
                extraProps = "kmptargets.hierarchyTemplate=false\n",
            )
        assertTrue("appleMain" in sourceSets, "global off should keep KGP default: $sourceSets")
    }

    @Test
    fun `given global property false but project override true when applied then minimal template wins`(
        @TempDir dir: Path
    ) {
        val sourceSets =
            sourceSetsOf(
                dir,
                "iosArm64,iosX64,iosSimulatorArm64",
                extraProps = "kmptargets.hierarchyTemplate=false\n",
            ) {
                it.extensions.getByType(KmpTargetsExtension::class.java).hierarchyTemplate.set(true)
            }
        assertTrue("iosMain" in sourceSets, sourceSets.toString())
        assertFalse("appleMain" in sourceSets, "project override should win: $sourceSets")
    }

    @Test
    fun `given a plain Kotlin Multiplatform module without kmp-targets when applied then KGP default is untouched`(
        @TempDir dir: Path
    ) {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.iosArm64()
        kotlin.iosX64()
        kotlin.iosSimulatorArm64()
        (project as ProjectInternal).evaluate()
        val sourceSets = kotlin.sourceSets.names.toSet()
        assertTrue("appleMain" in sourceSets, "non-interference: KGP default expected: $sourceSets")
        assertTrue("nativeMain" in sourceSets, sourceSets.toString())
    }

    @Test
    fun `given jvm only when applied then jvmMain still depends on commonMain`(@TempDir dir: Path) {
        // jvm is a standalone family with no intermediate, so it attaches directly to commonMain.
        // The minimal template must keep that edge, or commonMain code is excluded (issue #16).
        assertTrue(
            "commonMain" in dependsOnOf(dir, "jvm", "jvmMain"),
            "commonMain edge dropped (#16)",
        )
    }

    @Test
    fun `given single iosArm64 when applied then iosArm64Main still depends on commonMain`(
        @TempDir dir: Path
    ) {
        // A single-leaf native family collapses all the way to a leaf attached directly to
        // commonMain — same dropped-edge hazard as jvm (issue #16).
        assertTrue(
            "commonMain" in dependsOnOf(dir, "iosArm64", "iosArm64Main"),
            "commonMain edge dropped (#16)",
        )
    }

    @Test
    fun `given core and KGP applied when reading the active set then the spec matches the minimal tree`(
        @TempDir dir: Path
    ) {
        // Proves the active-set -> spec wiring uses selection ∩ resolvedSupported. Materialization
        // is asserted by the source-set tests above; here we pin the pure decision on a real
        // extension fed by a real KMP_TARGETS property.
        dir.resolve("gradle.properties")
            .writeText("KMP_TARGETS=iosArm64,iosX64,iosSimulatorArm64\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        val active = ext.resolvedSelection() intersect KmpTargetSet.all
        val groups = computeHierarchySpec(active).roots.filterIsInstance<HierarchyNode.Group>()
        assertEquals(setOf(HierarchyGroup.IOS), groups.map { it.group }.toSet())
    }

    private fun sourceSetsOf(
        dir: Path,
        kmpTargets: String,
        extraProps: String = "",
        configure: (Project) -> Unit = {},
    ): Set<String> {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=$kmpTargets\n$extraProps")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        configure(project)
        (project as ProjectInternal).evaluate()
        return project.extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .sourceSets
            .names
            .toSet()
    }

    /** Direct `dependsOn` source-set names of [sourceSet] after the minimal template is applied. */
    private fun dependsOnOf(dir: Path, kmpTargets: String, sourceSet: String): Set<String> {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=$kmpTargets\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        (project as ProjectInternal).evaluate()
        return project.extensions
            .getByType(KotlinMultiplatformExtension::class.java)
            .sourceSets
            .getByName(sourceSet)
            .dependsOn
            .map { it.name }
            .toSet()
    }
}
