package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KmpTargetsPluginTest {

    @Test
    fun `given plugin applied to project when extension is queried then KmpTargetsExtension is registered`(
        @TempDir dir: Path
    ) {
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.findByType(KmpTargetsExtension::class.java)
        assertNotNull(ext)
    }

    @Test
    fun `given plugin applied without any property when selection is read then equals defaultSelection default KmpTargetSet all`(
        @TempDir dir: Path
    ) {
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.all, ext.resolvedSelection())
    }

    @Test
    fun `given kmptargets targets gradle property is set when selection is read then it reflects the parsed value`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=android,iosArm64\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Native.Apple.Ios.Arm64),
            ext.resolvedSelection(),
        )
    }

    @Test
    fun `given kmptargets targets gradle property is appleMobile when selection is read then it equals KmpTargetSet appleMobile`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=appleMobile\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.appleMobile, ext.resolvedSelection())
    }

    @Test
    fun `given user defaultSelection override when plugin applied without property then selection equals that default`(
        @TempDir dir: Path
    ) {
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        ext.defaultSelection.set(KmpTargetSet.web)
        assertEquals(KmpTargetSet.web, ext.resolvedSelection())
    }

    @Test
    fun `given kmptargets targets is invalid when plugin is applied then a GradleException surfaces in the failure chain`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm65\n")
        val project = newProject(dir)
        val ex =
            assertFailsWith<Exception> { project.pluginManager.apply("com.rsicarelli.kmptargets") }
        // Apply wraps the GradleException; walk the chain to find the one carrying the parser's
        // message.
        val rootCause =
            generateSequence(ex as Throwable?) { it.cause }
                .firstOrNull { it.message?.contains("kmptargets.targets") == true }
        assertNotNull(
            rootCause,
            "expected kmptargets.targets-tagged cause in chain, root: ${ex.message}",
        )
        assertTrue(
            rootCause.message!!.contains("iosArm65"),
            "expected message to include offending token, got: ${rootCause.message}",
        )
        assertTrue(
            !rootCause.message!!.contains("kmptargets.targets: kmptargets.targets"),
            "expected a single property prefix, got: ${rootCause.message}",
        )
        assertTrue(
            rootCause.message!!.contains("did you mean 'iosArm64'?"),
            "expected canonical suggestion, got: ${rootCause.message}",
        )
    }

    @Test
    fun `given kmptargets targets in local properties when plugin applied then selection reflects that value`(
        @TempDir dir: Path
    ) {
        dir.resolve("local.properties").writeText("kmptargets.targets=android\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Android), ext.resolvedSelection())
    }

    @Test
    fun `given kmptargets targets in both gradle properties and local properties when plugin applied then gradle property wins`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        dir.resolve("local.properties").writeText("kmptargets.targets=android\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), ext.resolvedSelection())
    }

    @Test
    fun `given kmptargets targets is blank when plugin applied then selection falls back to default`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        // A blank property is not "I want zero targets" — it's "I'm not overriding"; the default
        // wins.
        assertEquals(KmpTargetSet.all, ext.resolvedSelection())
    }

    @Test
    fun `given kmptargets targets narrows to nothing via minus when selection is read then it is empty not the default`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm,-jvm\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        // An explicit selection narrowed to nothing means "build nothing", not "build everything".
        assertEquals(KmpTargetSet.empty, ext.resolvedSelection())
    }

    @Test
    fun `given committed config file with targets key when plugin applied then selection reflects it`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.properties").writeText("kmptargets.targets=android\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Android), ext.resolvedSelection())
    }

    @Test
    fun `given personal and committed config files when plugin applied then the personal file wins`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.properties").writeText("kmptargets.targets=android\n")
        dir.resolve("kmp-targets.local.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Desktop), ext.resolvedSelection())
    }

    @Test
    fun `given committed config file and gradle properties when plugin applied then the committed file wins`(
        @TempDir dir: Path
    ) {
        // The dedicated file is the consolidation point: once a team adopts it, a stale key left
        // behind in gradle.properties must not silently override it (issue #30).
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        dir.resolve("kmp-targets.properties").writeText("kmptargets.targets=android\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Android), ext.resolvedSelection())
    }

    @Test
    fun `given cli property and personal config file when plugin applied then the cli value wins`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.local.properties").writeText("kmptargets.targets=android\n")
        val project = newProject(dir)
        project.gradle.startParameter.projectProperties = mapOf("kmptargets.targets" to "jvm")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Desktop), ext.resolvedSelection())
    }

    @Test
    fun `given committed config file with blank targets value when plugin applied then selection falls back to default`(
        @TempDir dir: Path
    ) {
        // Blank is "present but not overriding" — it falls through to the default selection, and
        // shadows lower layers rather than deferring to them (same contract as gradle.properties).
        dir.resolve("kmp-targets.properties").writeText("kmptargets.targets=\n")
        dir.resolve("gradle.properties").writeText("kmptargets.targets=android\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.all, ext.resolvedSelection())
    }

    @Test
    fun `given committed config file with unknown key when plugin applied then the build fails with a suggestion`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.properties").writeText("kmptargets.tagets=jvm\n")
        val project = newProject(dir)
        val ex =
            assertFailsWith<Exception> { project.pluginManager.apply("com.rsicarelli.kmptargets") }
        val rootCause =
            generateSequence(ex as Throwable?) { it.cause }
                .firstOrNull { it.message?.contains("kmp-targets.properties") == true }
        assertNotNull(rootCause, "expected file-tagged cause in chain, root: ${ex.message}")
        assertTrue(
            rootCause.message!!.contains("did you mean 'kmptargets.targets'?"),
            "expected suggestion, got: ${rootCause.message}",
        )
    }

    @Test
    fun `given personal config file with unknown key when plugin applied then the build fails naming that file`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.local.properties").writeText("kmptargets.hierachyTemplate=false\n")
        val project = newProject(dir)
        val ex =
            assertFailsWith<Exception> { project.pluginManager.apply("com.rsicarelli.kmptargets") }
        val rootCause =
            generateSequence(ex as Throwable?) { it.cause }
                .firstOrNull { it.message?.contains("kmp-targets.local.properties") == true }
        assertNotNull(rootCause, "expected file-tagged cause in chain, root: ${ex.message}")
        assertTrue(
            rootCause.message!!.contains("did you mean 'kmptargets.hierarchyTemplate'?"),
            "expected suggestion, got: ${rootCause.message}",
        )
    }

    @Test
    fun `given the legacy placeholder task name when listed then it does not exist`(
        @TempDir dir: Path
    ) {
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        // The bootstrap placeholder task has been replaced with the real selector wiring.
        assertEquals(null, project.tasks.findByName("kmpTargetsHello"))
    }

    @Test
    fun `given selection overlaps supported when deciding empty-overlap warning then it does not warn`() {
        // androidTarget is in the supported set, so the selection is NOT disjoint — warning here
        // would be self-contradictory (issue #10).
        assertFalse(
            shouldWarnEmptyOverlap(
                selection = KmpTargetSet.of(KmpTarget.Jvm.Android),
                supported = KmpTargetSet.all,
            )
        )
    }

    @Test
    fun `given selection disjoint from supported when deciding empty-overlap warning then it warns`() {
        assertTrue(
            shouldWarnEmptyOverlap(
                selection = KmpTargetSet.of(KmpTarget.Web.WasmJs),
                supported = KmpTargetSet.of(KmpTarget.Jvm.Desktop),
            )
        )
    }

    private fun newProject(dir: Path): Project =
        ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
}
