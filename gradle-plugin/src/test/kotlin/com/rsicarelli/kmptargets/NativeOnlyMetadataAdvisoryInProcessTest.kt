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
 * The native-only-metadata advisory (#72) wired into eager registration: when a module supports the
 * JVM family but a registration pass ends with no JVM-family leaf registered while other targets
 * did register, the advisory names the trap — the platform klibs compile, but the
 * `*KotlinMetadata*` compilations reject JVM-flavored constructs — and the scoped build-logic gate
 * (`registered(jvmFamily).isEmpty()`), failing with the identical text under
 * `kmptargets.strict=true` through the same [warnOrFail] escalation point as every other advisory.
 *
 * Ordering doctrine under strict: the cause advisories (empty-overlap, android-without-AGP) are
 * emitted first and win the exception; native-only-metadata is a consequence channel, evaluated
 * last next to its mutually-exclusive sibling inert (#71).
 *
 * Strict tests register `wasmJs` (host-agnostic) on purpose: a strict test registering a native
 * leaf would trip the host-impossible advisory's exception first on a host that cannot compile it
 * (CI runs on Linux). Warn-mode tests may register `iosArm64` — the host advisory only warns there.
 */
class NativeOnlyMetadataAdvisoryInProcessTest {

    @Test
    fun `given jvm family and ios supported when an ios-only selection registers then the native-only advisory fires`(
        @TempDir dir: Path
    ) {
        // The issue's headline case: an android-less lane drops every JVM-family leaf, the native
        // leaf registers fine, and commonMain silently becomes a JVM-less shared fragment.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { androidTarget + jvm + iosArm64 }
        assertEquals(setOf("iosArm64"), registeredTargets(project))
        assertTrue(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given a selection that registers the jvm leaf when supports is declared then the advisory stays silent`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm + iosArm64 }
        assertEquals(setOf("jvm", "iosArm64"), registeredTargets(project))
        assertFalse(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given a fully inert module when supports is declared then inert fires and native-only does not`(
        @TempDir dir: Path
    ) {
        // Disjointness with #71 in-process: zero registrations is the inert state; this advisory
        // requires something to have registered. Exactly one of the two signals per module state.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,-jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        assertEquals(emptySet(), registeredTargets(project))
        assertTrue(extension.inertWarned)
        assertFalse(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given no jvm-family leaf in supports when a native-only selection registers then the advisory stays silent`(
        @TempDir dir: Path
    ) {
        // A pure-native/web module under a native lane never promised a JVM-flavored commonMain.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { iosArm64 + wasmJs }
        assertEquals(setOf("iosArm64"), registeredTargets(project))
        assertFalse(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given supports never called when the project configures then no native-only signal fires`(
        @TempDir dir: Path
    ) {
        // Explicit-selection doctrine: never declaring supports { } registers nothing on purpose —
        // register() never runs, so the advisory cannot fire, and the info input reports false.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        assertFalse(extension.nativeOnlyMetadataWarned)
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertFalse(task.nativeOnlyMetadata.get())
    }

    @Test
    fun `given strict on and a web-only selection when jvm is supported then the build fails with the native-only text`(
        @TempDir dir: Path
    ) {
        // wasmJs keeps this cross-host stable (see the class KDoc). Verbatim equality also proves
        // every sibling stayed silent: had one fired, its text would have won the exception.
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=wasmJs\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val ex = assertFailsWith<GradleException> { ext(project).supports { jvm + wasmJs } }
        assertEquals(nativeOnlyMetadataWarning(project.path), ex.message)
    }

    @Test
    fun `given strict on when the native-only advisory throws then it leaves no dedup bookkeeping behind`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=wasmJs\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        assertFailsWith<GradleException> { extension.supports { jvm + wasmJs } }
        // Mirrors the family doctrine: the flag is recorded AFTER warnOrFail, so a strict failure
        // does not mark the module as already-warned.
        assertFalse(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given the advisory already fired when a second still-jvm-less supports union runs then it does not repeat`(
        @TempDir dir: Path
    ) {
        // Strict makes "does not repeat" assertable: pre-seeding the dedup flag must leave both
        // passes with nothing to throw (mirrors the inert suite's pre-seed pattern). The condition
        // stays true across both unions — jvm supported, only wasmJs registered.
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=wasmJs\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.nativeOnlyMetadataWarned = true
        extension.supports { jvm + wasmJs }
        extension.supports { wasmJs }
        assertEquals(setOf("wasmJs"), registeredTargets(project))
    }

    @Test
    fun `given a later supports union that registers the jvm leaf then the advisory never fires`(
        @TempDir dir: Path
    ) {
        // The resolution guarantee: once a JVM-family leaf registers, the predicate is permanently
        // false (registration is one-way). The first union has no JVM-family leaf in supported, so
        // it is silent; the second registers jvm and the module can never trip the trap again.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { iosArm64 }
        assertFalse(extension.nativeOnlyMetadataWarned)
        extension.supports { jvm }
        assertEquals(setOf("jvm", "iosArm64"), registeredTargets(project))
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Desktop),
            extension.registered(KmpTargetSet.jvmFamily),
        )
        assertFalse(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given an android overlap skipped by the AGP guard when ios also registers then both advisories fire`(
        @TempDir dir: Path
    ) {
        // Cause and consequence compose: the #51 skip keeps androidTarget out of registered(), so
        // the pass ends alive (ios registered) but JVM-less — android-without-AGP names why the
        // leaf is missing, native-only-metadata names what that does to commonMain.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=android,iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { androidTarget + iosArm64 }
        assertEquals(setOf("iosArm64"), registeredTargets(project))
        assertTrue(extension.androidAgpWarned)
        assertTrue(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given strict on and the AGP-skip composition when supports is declared then the build fails with the android text`(
        @TempDir dir: Path
    ) {
        // Cause wins: android-without-AGP is emitted before the consequence advisories, so under
        // strict its text takes the exception and native-only is never reached.
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=android,wasmJs\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        val ex = assertFailsWith<GradleException> { extension.supports { androidTarget + wasmJs } }
        assertEquals(androidWithoutAgpWarning(project.path), ex.message)
        assertFalse(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given a renamed jvm target when it registers then the advisory stays silent`(
        @TempDir dir: Path
    ) {
        // The predicate is leaf-based, not name-based: a jvm leaf renamed via targetName still
        // carries KmpTarget.Jvm.Desktop in registered(), so the fragment stays JVM-flavored.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.targetName(KmpTargetsDsl.jvm, "desktop")
        extension.supports { jvm + iosArm64 }
        assertEquals(setOf("desktop", "iosArm64"), registeredTargets(project))
        assertFalse(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given an ios-only registration when configured on any host then the advisory fires host-blind`(
        @TempDir dir: Path
    ) {
        // Cross-host stability: registration is host-blind, so iosArm64 registers on macOS, Linux,
        // and Windows alike — and a JVM-less fragment is JVM-less on every one of them. The
        // predicate never consults the host.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm + iosArm64 }
        assertEquals(setOf("iosArm64"), registeredTargets(project))
        assertTrue(extension.nativeOnlyMetadataWarned)
    }

    @Test
    fun `given a jvm-less module when the native-only info input is read then it is true`(
        @TempDir dir: Path
    ) {
        // Same decision source as the advisory (shouldWarnNativeOnlyMetadata over
        // resolvedSupported()/registered()), realized lazily at task-graph calculation.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm + iosArm64 }
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertTrue(task.nativeOnlyMetadata.get())
    }

    @Test
    fun `given a module with a registered jvm leaf when the native-only info input is read then it is false`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,iosArm64\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm + iosArm64 }
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertFalse(task.nativeOnlyMetadata.get())
    }

    @Test
    fun `given no KGP applied when the native-only info input is read then it is false`(
        @TempDir dir: Path
    ) {
        // Without KGP nothing registers — no metadata compilation exists either, so there is no
        // trap to signal. The provider's KGP guard keeps the report quiet.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        ext(project).supports { jvm + iosArm64 }
        val task = project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
        assertFalse(task.nativeOnlyMetadata.get())
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
