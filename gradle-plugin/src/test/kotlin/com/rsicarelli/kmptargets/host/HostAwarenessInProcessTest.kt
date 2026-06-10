package com.rsicarelli.kmptargets.host

import com.rsicarelli.kmptargets.KmpTargetsExtension
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Proves the host-awareness advisory is wired into eager registration without changing it: the real
 * `HostManager` call path runs during `supports { … }` and the registered set is exactly `selection
 * ∩ supported` no matter what the running host can compile. Host-*policy* outcomes (which leaves
 * warn on which host) are covered by [HostCompatibilityTest] with simulated enabled-sets — nothing
 * here may assert a host-specific value.
 */
class HostAwarenessInProcessTest {

    @Test
    fun `given native targets impossible on some host when configured then they still register and configuration succeeds`(
        @TempDir dir: Path
    ) {
        // appleMobile + linux + mingw cannot all be compilable on any single host, so whatever
        // machine runs this test exercises the warning path for at least zero leaves — and the
        // registered set must be the full selection regardless.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=all\n")
        val project =
            newEvaluatedProject(dir) { ext -> ext.supports { appleMobile + linux + mingw } }
        assertRegisteredTargets(
            project,
            "iosArm64",
            "iosSimulatorArm64",
            "iosX64",
            "linuxX64",
            "linuxArm64",
            "mingwX64",
        )
    }

    @Test
    fun `given supports called twice when configured then hostWarned stays a subset of the active set`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=all\n")
        lateinit var ext: KmpTargetsExtension
        val project =
            newEvaluatedProject(dir) {
                ext = it
                it.supports { linux }
                it.supports { linux + iosArm64 }
            }
        // The union registers exactly once per leaf, and only registered leaves can have been
        // named in a host warning — never JVM/Web, never something outside the active set.
        assertRegisteredTargets(project, "linuxX64", "linuxArm64", "iosArm64")
        val active = ext.resolvedSelection() intersect ext.resolvedSupported()
        assertTrue(
            ext.hostWarned.all { it in active },
            "hostWarned ${ext.hostWarned} must be a subset of active ${active.members}",
        )
    }

    private fun newEvaluatedProject(dir: Path, configure: (KmpTargetsExtension) -> Unit): Project {
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        configure(project.extensions.getByType(KmpTargetsExtension::class.java))
        (project as ProjectInternal).evaluate()
        return project
    }

    private fun assertRegisteredTargets(project: Project, vararg expected: String) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val actual = kotlin.targets.names.filter { it != "metadata" }.toSet()
        assertEquals(expected.toSet(), actual)
    }
}
