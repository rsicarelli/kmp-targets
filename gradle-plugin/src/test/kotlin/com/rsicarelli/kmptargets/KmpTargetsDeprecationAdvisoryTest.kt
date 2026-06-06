package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The deprecation advisory (#43): registering a leaf Kotlin marks deprecated warns once per leaf
 * per module — and fails with the identical text under `kmptargets.strict=true`, through the same
 * [warnOrFail] escalation point as every other advisory. Pure decision/message facts are covered by
 * the unit tests next to those functions; this suite proves the wiring inside eager registration,
 * KGP applied before `supports` so failures propagate unwrapped.
 */
class KmpTargetsDeprecationAdvisoryTest {

    private val macosX64 = KmpTarget.Native.Apple.Macos.X64
    private val watchosX64 = KmpTarget.Native.Apple.Watchos.X64

    @Test
    fun `given a deprecated leaf in the active set when supports is declared then it still registers and the build succeeds`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=macosX64,jvm\n")
        val project = newProjectWithKgp(dir)
        ext(project).supports { appleDesktop + jvm }
        // Signal, never filtering: the deprecated leaf registers exactly as selected.
        assertEquals(setOf("macosX64", "jvm"), registeredTargets(project))
    }

    @Test
    fun `given strict on and a deprecated leaf in the active set when supports is declared then the build fails with the advisory text`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=macosX64,jvm\nkmptargets.strict=true\n")
        val project = newProjectWithKgp(dir)
        val ex = assertFailsWith<GradleException> { ext(project).supports { appleDesktop + jvm } }
        assertEquals(deprecatedTargetsWarning(project.path, KmpTargetSet.of(macosX64)), ex.message)
    }

    @Test
    fun `given a second supports union introducing another deprecated leaf when registered then only the fresh leaf is flagged`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=macosX64,watchosX64,jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { appleDesktop }
        assertEquals(setOf<KmpTarget>(macosX64), extension.deprecatedWarned)
        extension.supports { appleWatch }
        // The union re-runs registration; only the newly active deprecated leaf joins the dedup
        // set — macosX64 is not re-flagged.
        assertEquals(setOf<KmpTarget>(macosX64, watchosX64), extension.deprecatedWarned)
    }

    @Test
    fun `given only non-deprecated targets including iosX64 when supports is declared then no deprecation advisory fires`(
        @TempDir dir: Path
    ) {
        dir.resolve("gradle.properties").writeText("kmptargets.targets=iosX64,jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { appleMobile + jvm }
        assertEquals(emptySet(), extension.deprecatedWarned)
    }

    @Test
    fun `given a deprecated leaf selected but unsupported when supports is declared then no advisory fires`(
        @TempDir dir: Path
    ) {
        // The advisory keys off the ACTIVE set (selection ∩ supported): a deprecated leaf the
        // module cannot build never registers, so there is nothing to flag.
        dir.resolve("gradle.properties").writeText("kmptargets.targets=macosX64,jvm\n")
        val project = newProjectWithKgp(dir)
        val extension = ext(project)
        extension.supports { jvm }
        assertEquals(emptySet(), extension.deprecatedWarned)
    }

    // -- pure message facts ----------------------------------------------------------------------

    @Test
    fun `given deprecated leaves when the warning is built then it names the sorted ids the version and the docs page`() {
        val message = deprecatedTargetsWarning(":lib", KmpTargetSet.of(watchosX64, macosX64))
        assertContains(message, "[macosX64, watchosX64]")
        assertContains(message, ":lib")
        assertContains(message, "since Kotlin 2.3.20")
        assertContains(message, "https://kotlinlang.org/docs/native-target-support.html")
        assertContains(message, "still registered")
    }

    @Test
    fun `given an active set and already warned leaves when fresh deprecated is computed then only new deprecated leaves remain`() {
        val active = KmpTargetSet.of(macosX64, watchosX64, KmpTarget.Jvm.Desktop)
        assertEquals(
            setOf<KmpTarget>(watchosX64),
            freshDeprecated(active, alreadyWarned = setOf(macosX64)),
        )
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
