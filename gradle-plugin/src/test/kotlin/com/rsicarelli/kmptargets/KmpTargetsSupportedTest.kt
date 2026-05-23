package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Proves the capability ∩ selection model end-to-end against the *real* Kotlin Multiplatform plugin
 * applied in-process. These are the load-bearing "does the approach actually register the right KGP
 * targets" tests — they assert on `kotlin.targets`, not on our own bookkeeping.
 *
 * Ordering note: scenarios that declare a `supported` set apply the core plugin first (queuing
 * registration), declare supported, then apply KGP last — mirroring exactly what a convention
 * plugin does. This is what lets a narrowed `supported` set be visible before any target is
 * registered, without `afterEvaluate`.
 */
class KmpTargetsSupportedTest {

    @Test
    fun `given no supported declared and KMP_TARGETS jvm,iosArm64 when applied then exactly those targets register`(
        @TempDir dir: Path
    ) {
        val project = coreThenKgp(dir, kmpTargets = "jvm,iosArm64", supported = null)
        assertRegisteredTargets(project, "jvm", "iosArm64")
    }

    @Test
    fun `given supported apple and KMP_TARGETS jvm,iosArm64 when applied then only iosArm64 registers`(
        @TempDir dir: Path
    ) {
        val project = coreThenKgp(dir, kmpTargets = "jvm,iosArm64", supported = KmpTargetSet.apple)
        assertRegisteredTargets(project, "iosArm64")
    }

    @Test
    fun `given supported jvm only and KMP_TARGETS wasmJs when applied then no targets register`(
        @TempDir dir: Path
    ) {
        val project =
            coreThenKgp(
                dir,
                kmpTargets = "wasmJs",
                supported = KmpTargetSet.of(KmpTarget.Jvm.Desktop),
            )
        assertRegisteredTargets(project /* none */)
        // warn + skip: skipping is the behavioural guarantee asserted here.
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertTrue(ext.registered.isEmpty())
    }

    @Test
    fun `given supported mobile then web declared before KGP and KMP_TARGETS iosArm64,wasmJs when applied then union registers`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=iosArm64,wasmJs\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        KmpTargets.declareSupported(project, KmpTargetSet.mobile)
        KmpTargets.declareSupported(project, KmpTargetSet.web)
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        assertRegisteredTargets(project, "iosArm64", "wasmJs")
    }

    @Test
    fun `given supported web declared after KGP already applied and KMP_TARGETS iosArm64,wasmJs when applied then composition still unions incrementally`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=iosArm64,wasmJs\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        KmpTargets.declareSupported(project, KmpTargetSet.mobile)
        // KGP applied here (as the first convention plugin would) — registers the mobile slice.
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        // A second convention plugin contributes web *after* KGP is already applied.
        KmpTargets.declareSupported(project, KmpTargetSet.web)
        assertRegisteredTargets(project, "iosArm64", "wasmJs")
    }

    @Test
    fun `given kmptargets supported property apple and KMP_TARGETS all when applied then only apple targets register`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=all\nkmptargets.supported=apple\n")
        val project = newProject(dir)
        // Property-declared support must be visible before registration, so core-first / KGP-last.
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        // supported=apple now spans iOS+macOS+watchOS+tvOS; KMP_TARGETS=all ∩ apple = apple.
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val actual = kotlin.targets.names.filter { it != "metadata" }.toSet()
        assertEquals(KmpTargetSet.apple.members.map { it.id }.toSet(), actual)
    }

    @Test
    fun `given a selection spanning every new native family when applied then each leaf registers with real KGP`(
        @TempDir dir: Path
    ) {
        // One representative leaf from each newly-shipped family (iosX64, Linux, watchOS incl.
        // device, tvOS, MinGW, Android Native). Proves each registerTarget branch wires to a real
        // KGP target function and not, say, the wrong one.
        val project =
            coreThenKgp(
                dir,
                kmpTargets =
                    "iosX64,linuxX64,linuxArm64,watchosArm64,watchosDeviceArm64,tvosArm64,mingwX64,androidNativeArm64",
                supported = null,
            )
        assertRegisteredTargets(
            project,
            "iosX64",
            "linuxX64",
            "linuxArm64",
            "watchosArm64",
            "watchosDeviceArm64",
            "tvosArm64",
            "mingwX64",
            "androidNativeArm64",
        )
    }

    @Test
    fun `given KMP_TARGETS apple when applied then every Apple platform registers including watchOS and tvOS`(
        @TempDir dir: Path
    ) {
        val project = coreThenKgp(dir, kmpTargets = "apple", supported = null)
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val actual = kotlin.targets.names.filter { it != "metadata" }.toSet()
        assertEquals(KmpTargetSet.apple.members.map { it.id }.toSet(), actual)
    }

    @Test
    fun `given KMP_TARGETS native when applied then it registers every native leaf and no jvm or web`(
        @TempDir dir: Path
    ) {
        val project = coreThenKgp(dir, kmpTargets = "native", supported = null)
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val actual = kotlin.targets.names.filter { it != "metadata" }.toSet()
        assertEquals(KmpTargetSet.native.members.map { it.id }.toSet(), actual)
        assertTrue("jvm" !in actual && "js" !in actual)
    }

    @Test
    fun `given selection disjoint from supported when emptyOverlapWarning then message names path selection and supported`() {
        val msg =
            KmpTargets.emptyOverlapWarning(
                path = ":feature-mobile",
                selection = KmpTargetSet.of(KmpTarget.Jvm.Desktop),
                supported = KmpTargetSet.appleMobile,
            )
        assertTrue(msg.contains(":feature-mobile"), msg)
        assertTrue(msg.contains("jvm"), msg)
        assertTrue(msg.contains("iosArm64"), msg)
    }

    private fun coreThenKgp(dir: Path, kmpTargets: String, supported: KmpTargetSet?): Project {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=$kmpTargets\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        if (supported != null) KmpTargets.declareSupported(project, supported)
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        return project
    }

    private fun newProject(dir: Path): Project =
        ProjectBuilder.builder().withProjectDir(dir.toFile()).build()

    /** Asserts the KGP targets registered equal [expected] (ignoring KGP's implicit `metadata`). */
    private fun assertRegisteredTargets(project: Project, vararg expected: String) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val actual = kotlin.targets.names.filter { it != "metadata" }.toSet()
        assertEquals(expected.toSet(), actual)
    }
}
