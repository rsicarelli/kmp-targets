package com.rsicarelli.kmptargets.info

import com.rsicarelli.kmptargets.KmpTargetsExtension
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import com.rsicarelli.kmptargets.parser.presetNames
import com.rsicarelli.kmptargets.source.ConfigKeys
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * In-process proof of the `kmpTargetsInfo` wiring: the task's inputs are plain `String` primitives,
 * realized lazily, that reflect the post-body state of the extension and the config-layer origin
 * that won. KGP is never applied here — the report must work (and be explicit about "nothing
 * registers") without it.
 */
class KmpTargetsInfoTaskTest {

    private val mobileIds = listOf("androidTarget", "iosArm64", "iosSimulatorArm64", "iosX64")
    private val allIds = KmpTarget.all.map { it.id }.sorted()

    @Test
    fun `given no selection source and no defaultSelection override when info task is realized then selection is all and origin is the built-in default`(
        @TempDir dir: Path
    ) {
        val task = infoTask(newAppliedProject(dir))
        assertEquals(allIds, task.selectionIds.get())
        assertEquals(OriginLabels.DEFAULT_BUILTIN, task.originLabel.get())
    }

    @Test
    fun `given defaultSelection overridden after apply when info task is realized then origin is the build-logic default`(
        @TempDir dir: Path
    ) {
        val project = newAppliedProject(dir)
        // Build-script-body timing: the override happens AFTER apply, so the origin label must be
        // resolved lazily to see it.
        project.extensions
            .getByType(KmpTargetsExtension::class.java)
            .defaultSelection
            .set(KmpTargetSet.web)
        val task = infoTask(project)
        assertEquals(listOf("js", "wasmJs", "wasmWasi"), task.selectionIds.get())
        assertEquals(OriginLabels.DEFAULT_BUILD_LOGIC, task.originLabel.get())
    }

    @Test
    fun `given selection via cli property when info task is realized then origin is the command line and selection is the mobile leaves`(
        @TempDir dir: Path
    ) {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.gradle.startParameter.projectProperties = mapOf(ConfigKeys.TARGETS to "mobile")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        val task = infoTask(project)
        assertEquals(mobileIds, task.selectionIds.get())
        assertEquals(OriginLabels.cli(ConfigKeys.TARGETS), task.originLabel.get())
    }

    @Test
    fun `given selection only in local properties when info task is realized then origin is local properties`(
        @TempDir dir: Path
    ) {
        dir.resolve("local.properties").writeText("kmptargets.targets=jvm\n")
        val task = infoTask(newAppliedProject(dir))
        assertEquals(listOf("jvm"), task.selectionIds.get())
        assertEquals(OriginLabels.LOCAL_PROPERTIES, task.originLabel.get())
    }

    @Test
    fun `given selection in gradle properties when info task is realized then origin is the fused gradle properties label`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val task = infoTask(newAppliedProject(dir))
        assertEquals(OriginLabels.gradleProperties(ConfigKeys.TARGETS), task.originLabel.get())
    }

    @Test
    fun `given selection in the committed config file when info task is realized then origin is that file name`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.properties").writeText("kmptargets.targets=jvm\n")
        val task = infoTask(newAppliedProject(dir))
        assertEquals(ConfigKeys.COMMITTED_FILE, task.originLabel.get())
    }

    @Test
    fun `given selection in the personal config file when info task is realized then origin is that file name and it beats the committed file`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.properties").writeText("kmptargets.targets=android\n")
        dir.resolve("kmp-targets.local.properties").writeText("kmptargets.targets=jvm\n")
        val task = infoTask(newAppliedProject(dir))
        assertEquals(listOf("jvm"), task.selectionIds.get())
        assertEquals(ConfigKeys.LOCAL_FILE, task.originLabel.get())
    }

    @Test
    fun `given a blank selection value when info task is realized then origin falls back to the built-in default`(
        @TempDir dir: Path
    ) {
        // Blank shadows lower layers but does not override the default — the origin must say where
        // the selection actually came from, not which layer happened to carry the blank.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=\n")
        val task = infoTask(newAppliedProject(dir))
        assertEquals(allIds, task.selectionIds.get())
        assertEquals(OriginLabels.DEFAULT_BUILTIN, task.originLabel.get())
    }

    @Test
    fun `given supports mobile and selection all when info task is realized then registered equals the mobile leaves`(
        @TempDir dir: Path
    ) {
        val project = newAppliedProject(dir)
        project.extensions.getByType(KmpTargetsExtension::class.java).supports { mobile }
        val task = infoTask(project)
        assertEquals(mobileIds, task.registeredIds.get())
        assertEquals(mobileIds, task.supportedIds.get())
        assertTrue(task.supportsDeclared.get())
    }

    @Test
    fun `given supports called twice when info task is realized then supported reflects the union`(
        @TempDir dir: Path
    ) {
        val project = newAppliedProject(dir)
        val ext = project.extensions.getByType(KmpTargetsExtension::class.java)
        // The info task is realized BEFORE the second union — its providers must still observe the
        // final post-body state, not a snapshot taken at realization time.
        val task = infoTask(project)
        ext.supports { jvm }
        ext.supports { web }
        assertEquals(listOf("js", "jvm", "wasmJs", "wasmWasi"), task.supportedIds.get())
    }

    @Test
    fun `given supports never declared when info task is realized then supported and registered are empty and declared is false`(
        @TempDir dir: Path
    ) {
        val task = infoTask(newAppliedProject(dir))
        assertEquals(emptyList(), task.supportedIds.get())
        assertEquals(emptyList(), task.registeredIds.get())
        assertFalse(task.supportsDeclared.get())
    }

    @Test
    fun `given a selection disjoint from supported when info task is realized then registered is empty`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=wasmJs\n")
        val project = newAppliedProject(dir)
        project.extensions.getByType(KmpTargetsExtension::class.java).supports { jvm }
        val task = infoTask(project)
        assertEquals(emptyList(), task.registeredIds.get())
        assertEquals(listOf("wasmJs"), task.selectionIds.get())
        assertEquals(listOf("jvm"), task.supportedIds.get())
    }

    @Test
    fun `given the info task when vocabulary inputs are read then they match the parser presets and every leaf id`(
        @TempDir dir: Path
    ) {
        val task = infoTask(newAppliedProject(dir))
        assertEquals(presetNames, task.presetNames.get())
        assertEquals(allIds, task.leafIds.get())
    }

    @Test
    fun `given the plugin applied without KGP when tasks are listed then kmpTargetsInfo exists in the help group`(
        @TempDir dir: Path
    ) {
        val task = infoTask(newAppliedProject(dir))
        assertEquals("help", task.group)
    }

    private fun newAppliedProject(dir: Path): Project {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        return project
    }

    private fun infoTask(project: Project): KmpTargetsInfoTask =
        project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
}
