package com.rsicarelli.kmptargets.conventions

import com.rsicarelli.kmptargets.KmpTargetsExtension
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Proves the convention-plugin layer: applying an id declares the right supported set, applies KGP
 * for the consumer, and registers `supported ∩ selection` — including composition of multiple ids.
 * Plugins are applied by class (no published descriptor needed in-process); the end-to-end id-based
 * application is covered by the multi-module sample under `task ci`.
 */
class KmpConventionPluginsTest {

    @Test
    fun `given mobile convention applied when supported is read then it equals the mobile preset`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, kmpTargets = "iosArm64")
        project.pluginManager.apply(KmpMobileConventionPlugin::class.java)
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.mobile, ext.supported.get())
    }

    @Test
    fun `given mobile convention and KMP_TARGETS iosArm64 when applied then only iosArm64 registers and KGP is applied`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, kmpTargets = "iosArm64")
        project.pluginManager.apply(KmpMobileConventionPlugin::class.java)
        assertTrue(project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform"))
        assertRegisteredTargets(project, "iosArm64")
    }

    @Test
    fun `given apple convention applied when supported is read then it spans iOS macOS watchOS and tvOS`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, kmpTargets = "iosArm64")
        project.pluginManager.apply(KmpAppleConventionPlugin::class.java)
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.apple, ext.supported.get())
        assertTrue(KmpTarget.Native.Apple.Watchos.Arm64 in ext.supported.get())
        assertTrue(KmpTarget.Native.Apple.Tvos.Arm64 in ext.supported.get())
    }

    @Test
    fun `given library convention applied when supported is read then it equals every shipped target`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, kmpTargets = "iosArm64")
        project.pluginManager.apply(KmpLibraryConventionPlugin::class.java)
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.all, ext.supported.get())
    }

    @Test
    fun `given jvm convention applied when supported is read then it equals jvm desktop only`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, kmpTargets = "jvm")
        project.pluginManager.apply(KmpJvmConventionPlugin::class.java)
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Desktop), ext.supported.get())
        assertRegisteredTargets(project, "jvm")
    }

    @Test
    fun `given library convention and KMP_TARGETS jvm,wasmJs when applied then both register`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, kmpTargets = "jvm,wasmJs")
        project.pluginManager.apply(KmpLibraryConventionPlugin::class.java)
        assertRegisteredTargets(project, "jvm", "wasmJs")
    }

    @Test
    fun `given mobile plus web conventions and KMP_TARGETS iosArm64,wasmJs when applied then supported unions and both register`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, kmpTargets = "iosArm64,wasmJs")
        project.pluginManager.apply(KmpMobileConventionPlugin::class.java)
        project.pluginManager.apply(KmpWebConventionPlugin::class.java)
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.mobile + KmpTargetSet.web, ext.supported.get())
        assertRegisteredTargets(project, "iosArm64", "wasmJs")
    }

    @Test
    fun `given jvm convention and KMP_TARGETS wasmJs when applied then nothing registers (empty overlap is skipped)`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, kmpTargets = "wasmJs")
        project.pluginManager.apply(KmpJvmConventionPlugin::class.java)
        assertRegisteredTargets(project /* none */)
    }

    @Test
    fun `given user already applied KGP when a convention plugin is applied then it fails with a helpful message`(
        @TempDir dir: Path
    ) {
        val project = projectWith(dir, kmpTargets = "iosArm64")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val ex =
            assertFailsWith<Exception> {
                project.pluginManager.apply(KmpMobileConventionPlugin::class.java)
            }
        val helpful =
            generateSequence(ex as Throwable?) { it.cause }
                .firstOrNull { it.message?.contains("convention plugin") == true }
        assertNotNull(helpful, "expected a helpful cause in chain, root: ${ex.message}")
        assertTrue(helpful.message!!.contains("kotlin(\"multiplatform\")"), helpful.message)
    }

    private fun projectWith(dir: Path, kmpTargets: String): Project {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=$kmpTargets\n")
        return ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
    }

    private fun assertRegisteredTargets(project: Project, vararg expected: String) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val actual = kotlin.targets.names.filter { it != "metadata" }.toSet()
        assertEquals(expected.toSet(), actual)
    }
}
