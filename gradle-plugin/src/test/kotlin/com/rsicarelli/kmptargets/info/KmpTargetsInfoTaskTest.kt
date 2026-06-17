package com.rsicarelli.kmptargets.info

import com.rsicarelli.kmptargets.KmpTargetsDsl
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
    fun `given a declared framework when the info task is realized then it exposes the framework facts`(
        @TempDir dir: Path
    ) {
        // No KGP here, so nothing registers and the framework attaches to nothing — but the
        // declared
        // facts (name, buildTypes, xcframework) are captured at appleFramework() time regardless.
        val project = newAppliedProject(dir)
        project.extensions
            .getByType(KmpTargetsExtension::class.java)
            .appleFramework("KotlinShared", xcframework = true)
        val task = infoTask(project)
        assertTrue(task.frameworkDeclared.get())
        assertEquals("KotlinShared", task.frameworkBaseName.get())
        assertEquals(listOf("DEBUG", "RELEASE"), task.frameworkBuildTypes.get())
        assertTrue(task.frameworkXcframework.get())
        assertEquals(emptyList(), task.frameworkAttachedIds.get())
    }

    @Test
    fun `given no declared framework when the info task is realized then frameworkDeclared is false`(
        @TempDir dir: Path
    ) {
        assertFalse(infoTask(newAppliedProject(dir)).frameworkDeclared.get())
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

    @Test
    fun `given the plugin without KGP when annotation inputs are read then host data degrades to empty`(
        @TempDir dir: Path
    ) {
        // Graceful degradation AND a classloading guard: without KGP the providers must never
        // touch HostManager, so the host annotations simply vanish from the report.
        val task = infoTask(newAppliedProject(dir))
        assertEquals(emptyList(), task.hostImpossibleIds.get())
        assertEquals("", task.hostLabel.get())
    }

    @Test
    fun `given targetName jvm desktop set after task realization when jvmRegisteredAs is read then it observes the rename`(
        @TempDir dir: Path
    ) {
        val project = newAppliedProject(dir)
        // Same lazy-wiring contract as the supports-union test: the task realizes BEFORE the body
        // calls targetName, so the provider must observe the final post-body state.
        val task = infoTask(project)
        project.extensions
            .getByType(KmpTargetsExtension::class.java)
            .targetName(KmpTargetsDsl.jvm, "desktop")
        assertEquals("desktop", task.jvmRegisteredAs.get())
    }

    @Test
    fun `given no rename when jvmRegisteredAs is read then it is absent`(@TempDir dir: Path) {
        val task = infoTask(newAppliedProject(dir))
        assertFalse(task.jvmRegisteredAs.isPresent)
    }

    @Test
    fun `given the plugin without KGP when the android annotation input is read then it degrades to false`(
        @TempDir dir: Path
    ) {
        // Without KGP nothing registers at all, so there is no skipped android leaf to annotate —
        // the provider's KGP guard keeps the report quiet (same wiring path as the host data).
        val project = newAppliedProject(dir)
        project.extensions.getByType(KmpTargetsExtension::class.java).supports { mobile }
        assertFalse(infoTask(project).androidWithoutAgp.get())
    }

    @Test
    fun `given the info task when deprecated ids are read then they are the pinned registry sorted`(
        @TempDir dir: Path
    ) {
        val task = infoTask(newAppliedProject(dir))
        assertEquals(listOf("macosX64", "tvosX64", "watchosX64"), task.deprecatedIds.get())
    }

    private fun newAppliedProject(dir: Path): Project {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        return project
    }

    private fun infoTask(project: Project): KmpTargetsInfoTask =
        project.tasks.getByName("kmpTargetsInfo") as KmpTargetsInfoTask
}
