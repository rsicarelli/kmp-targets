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
    fun `given plugin applied without any property when selection is read then equals fallback default KmpTargetSet all`(
        @TempDir dir: Path
    ) {
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.all, ext.selection.get())
    }

    @Test
    fun `given KMP_TARGETS gradle property is set when selection is read then it reflects the parsed value`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=android,iosArm64\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(
            KmpTargetSet.of(KmpTarget.Jvm.Android, KmpTarget.Native.Apple.Ios.Arm64),
            ext.selection.get(),
        )
    }

    @Test
    fun `given KMP_TARGETS gradle property is appleMobile when selection is read then it equals KmpTargetSet appleMobile`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=appleMobile\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.appleMobile, ext.selection.get())
    }

    @Test
    fun `given user fallback override when plugin applied without property then selection equals that fallback`(
        @TempDir dir: Path
    ) {
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        ext.fallback.set(KmpTargetSet.web)
        assertEquals(KmpTargetSet.web, ext.selection.get())
    }

    @Test
    fun `given KMP_TARGETS is invalid when plugin is applied then a GradleException surfaces in the failure chain`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=iosArm65\n")
        val project = newProject(dir)
        val ex =
            assertFailsWith<Exception> { project.pluginManager.apply("com.rsicarelli.kmptargets") }
        // Apply wraps the GradleException; walk the chain to find the one carrying the parser's
        // message.
        val rootCause =
            generateSequence(ex as Throwable?) { it.cause }
                .firstOrNull { it.message?.contains("KMP_TARGETS") == true }
        assertNotNull(rootCause, "expected KMP_TARGETS-tagged cause in chain, root: ${ex.message}")
        assertTrue(
            rootCause.message!!.contains("iosArm65"),
            "expected message to include offending token, got: ${rootCause.message}",
        )
        assertTrue(
            !rootCause.message!!.contains("KMP_TARGETS: KMP_TARGETS"),
            "expected a single property prefix, got: ${rootCause.message}",
        )
        assertTrue(
            rootCause.message!!.contains("did you mean 'iosArm64'?"),
            "expected canonical suggestion, got: ${rootCause.message}",
        )
    }

    @Test
    fun `given kmptargets supported property is invalid when plugin is applied then error names supported property once`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.supported=jvm,bogus\n")
        val project = newProject(dir)
        val ex =
            assertFailsWith<Exception> { project.pluginManager.apply("com.rsicarelli.kmptargets") }
        val rootCause =
            generateSequence(ex as Throwable?) { it.cause }
                .firstOrNull { it.message?.contains("kmptargets.supported") == true }
        assertNotNull(rootCause, "expected kmptargets.supported-tagged cause, root: ${ex.message}")
        assertTrue(
            rootCause.message!!.contains("kmptargets.supported: unknown target 'bogus'"),
            "expected message to name supported property and offending token, got: ${rootCause.message}",
        )
        assertTrue(
            !rootCause.message!!.contains("kmptargets.supported: KMP_TARGETS"),
            "expected no nested KMP_TARGETS label, got: ${rootCause.message}",
        )
    }

    @Test
    fun `given KMP_TARGETS in local properties when plugin applied then selection reflects that value`(
        @TempDir dir: Path
    ) {
        dir.resolve("local.properties").writeText("KMP_TARGETS=android\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.of(KmpTarget.Jvm.Android), ext.selection.get())
    }

    @Test
    fun `given KMP_TARGETS in both gradle properties and local properties when plugin applied then gradle property wins`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=iosArm64\n")
        dir.resolve("local.properties").writeText("KMP_TARGETS=android\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        assertEquals(KmpTargetSet.of(KmpTarget.Native.Apple.Ios.Arm64), ext.selection.get())
    }

    @Test
    fun `given KMP_TARGETS is blank when plugin applied then selection falls back to default`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        // A blank property is not "I want zero targets" — it's "I'm not overriding"; fallback wins.
        assertEquals(KmpTargetSet.all, ext.selection.get())
    }

    @Test
    fun `given KMP_TARGETS narrows to nothing via minus when selection is read then it is empty not fallback`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("KMP_TARGETS=jvm,-jvm\n")
        val project = newProject(dir)
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        // An explicit selection narrowed to nothing means "build nothing", not "build everything".
        assertEquals(KmpTargetSet.empty, ext.selection.get())
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
            KmpTargets.shouldWarnEmptyOverlap(
                selection = KmpTargetSet.of(KmpTarget.Jvm.Android),
                supported = KmpTargetSet.all,
            )
        )
    }

    @Test
    fun `given selection disjoint from supported when deciding empty-overlap warning then it warns`() {
        assertTrue(
            KmpTargets.shouldWarnEmptyOverlap(
                selection = KmpTargetSet.of(KmpTarget.Web.WasmJs),
                supported = KmpTargetSet.of(KmpTarget.Jvm.Desktop),
            )
        )
    }

    private fun newProject(dir: Path): Project =
        ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
}
