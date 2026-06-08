package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.doctor.DoctorData
import com.rsicarelli.kmptargets.doctor.KmpTargetsDoctorDataTask
import com.rsicarelli.kmptargets.doctor.KmpTargetsDoctorTask
import com.rsicarelli.kmptargets.doctor.parseDoctorData
import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
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
 * Doctor (#82) renders, it does not own predicates: these in-process tests pin that every
 * single-module property the report reads comes from the SAME extension state the advisories key
 * off — `kmpTargetsDoctor`'s `inertModule`/`emptyOverlap`/… are the very providers behind
 * `shouldWarnInertModule` and friends, never a re-implemented check. They also pin that the
 * `kmpTargetsDoctorData` emitter writes only this project's own primitives (the IP-safe carrier for
 * the closure check, #80).
 */
class KmpTargetsDoctorInProcessTest {

    @Test
    fun `given a disjoint module when the doctor inert and empty-overlap inputs are read then both are true`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=wasmJs\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm }
        val doctor = doctor(project)
        assertTrue(doctor.inertModule.get(), "inert sourced from the advisory's decision")
        assertTrue(doctor.emptyOverlap.get(), "empty-overlap sourced from the advisory's decision")
        assertEquals(emptyList(), doctor.registeredIds.get())
    }

    @Test
    fun `given an overlapping module when the doctor inert input is read then it is false`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { jvm }
        assertFalse(doctor(project).inertModule.get())
        assertEquals(listOf("jvm"), doctor(project).registeredIds.get())
    }

    @Test
    fun `given a JVM-less selection when the doctor native-only input is read then it is true`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { iosArm64 + jvm }
        assertTrue(doctor(project).nativeOnlyMetadata.get())
    }

    @Test
    fun `given supports never declared when the doctor supportsDeclared input is read then it is false`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=jvm\n")
        val project = newProjectWithKgp(dir)
        assertFalse(doctor(project).supportsDeclared.get())
    }

    @Test
    fun `given a registered deprecated leaf when the doctor deprecated input is read then it names it`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=watchosX64\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports(KmpTargetSet.of(KmpTarget.Native.Apple.Watchos.X64))
        assertEquals(listOf("watchosX64"), doctor(project).registeredDeprecatedIds.get())
    }

    @Test
    fun `given a registered module when the data emitter runs then it writes only its own primitives`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosArm64,jvm\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { iosArm64 + jvm }
        val data = project.tasks.getByName("kmpTargetsDoctorData") as KmpTargetsDoctorDataTask
        data.emit()
        val written = parseDoctorData(data.outputFile.get().asFile.readText())
        assertEquals(DoctorData(project.path, listOf("iosArm64", "jvm")), written)
    }

    @Test
    fun `given no KGP applied when the doctor inert input is read then it is false`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=wasmJs\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        ext(project).supports { jvm }
        assertFalse(doctor(project).inertModule.get())
    }

    private fun newProjectWithKgp(dir: Path): Project {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        return project
    }

    private fun ext(project: Project): KmpTargetsExtension =
        project.extensions.getByType(KmpTargetsExtension::class.java)

    private fun doctor(project: Project): KmpTargetsDoctorTask =
        project.tasks.getByName("kmpTargetsDoctor") as KmpTargetsDoctorTask
}
