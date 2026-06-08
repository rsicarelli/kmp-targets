package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTargetSet
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tripwire pinning the **actual** KGP 2.3.21 rule for the commonMain Kotlin **metadata compilation
 * task** (`compileCommonMainKotlinMetadata`) that the #73 recipe reasons about. Issue #73's open
 * question asks whether "≥1 native target" is the precise, stable condition for that compilation
 * existing. Measured here, it is **not**: the task tracks a genuinely **shared commonMain (≥2
 * platform targets)**, not native presence —
 *
 * - a lone `jvm` → absent (single target, commonMain not shared)
 * - a lone `iosArm64` → **absent** (a native target alone does *not* create it)
 * - `jvm + js` → **present** (two non-native targets share commonMain)
 * - `jvm + iosArm64` → present
 *
 * So the native-gating the issue describes belongs to KSP's `kspCommonMainKotlinMetadata` route
 * (KSP's own multiplatform wiring), not to KGP's metadata compilation. The recipe therefore gates
 * conservatively on `registered(KmpTargetSet.native).isEmpty()` for native-wired processors rather
 * than claiming anything about the bare compilation. If a KGP bump changes the target-count rule
 * pinned below, this test fails loudly and the recipe's premise must be revisited.
 *
 * In-process via `evaluate()` (no compile, so the native leaf is cross-host safe); a real build is
 * unnecessary to observe task existence.
 */
class CommonMainMetadataCompilationInvariantTest {

    @Test
    fun `given a single jvm target when the project evaluates then the commonMain metadata compilation is absent`(
        @TempDir dir: Path
    ) {
        val project = evaluated(dir, "jvm") { supports { jvm } }
        assertNull(
            project.tasks.findByName(COMMON_MAIN_METADATA),
            "a single target shares no commonMain, so KGP creates no $COMMON_MAIN_METADATA",
        )
    }

    @Test
    fun `given a single native target when the project evaluates then the commonMain metadata compilation is still absent`(
        @TempDir dir: Path
    ) {
        // The case that refutes "≥1 native target ⇒ metadata compilation exists": iosArm64 ALONE
        // does not create it. Native presence is not the trigger; target count is.
        val project = evaluated(dir, "iosArm64") { supports { iosArm64 } }
        assertNull(
            project.tasks.findByName(COMMON_MAIN_METADATA),
            "a lone native target shares no commonMain either — native presence is not the trigger",
        )
    }

    @Test
    fun `given jvm and web targets when the project evaluates then the commonMain metadata compilation exists despite no native target`(
        @TempDir dir: Path
    ) {
        // The pin that matters: two NON-native targets share commonMain, so the metadata
        // compilation exists even though registered(native) is empty.
        val project = evaluated(dir, "jvm,js") { supports { jvm + js } }
        assertTrue(ext(project).registered(KmpTargetSet.native).isEmpty(), "no native registered")
        assertNotNull(
            project.tasks.findByName(COMMON_MAIN_METADATA),
            "two shared targets create $COMMON_MAIN_METADATA with zero native targets",
        )
    }

    @Test
    fun `given jvm and a native target when the project evaluates then the commonMain metadata compilation exists`(
        @TempDir dir: Path
    ) {
        val project = evaluated(dir, "jvm,iosArm64") { supports { jvm + iosArm64 } }
        assertFalse(ext(project).registered(KmpTargetSet.native).isEmpty(), "native registered")
        assertNotNull(project.tasks.findByName(COMMON_MAIN_METADATA))
    }

    @Test
    fun `given the metadata compilation exists when kmpCompileAll is wired then it never depends on the metadata compilation`(
        @TempDir dir: Path
    ) {
        // Co-located with the invariant it protects (#77 must not re-introduce #71/#72): even when
        // KGP creates compileCommonMainKotlinMetadata, the kmpCompileAll umbrella depends only on
        // the registered platform compile tasks, never the metadata one.
        val project = evaluated(dir, "jvm,js") { supports { jvm + js } }
        assertNotNull(
            project.tasks.findByName(COMMON_MAIN_METADATA),
            "metadata compilation present",
        )
        val umbrella = project.tasks.getByName(KmpTargetsPlugin.COMPILE_ALL_TASK)
        val deps = umbrella.taskDependencies.getDependencies(umbrella).map { it.name }
        assertFalse(COMMON_MAIN_METADATA in deps, "kmpCompileAll excludes metadata: $deps")
        assertTrue(deps.containsAll(listOf("compileKotlinJvm", "compileKotlinJs")), "deps: $deps")
    }

    private fun evaluated(
        dir: Path,
        selection: String,
        block: KmpTargetsExtension.() -> Unit,
    ): Project {
        // umbrellaTasks on so the #77 metadata-exclusion pin can read kmpCompileAll; harmless to
        // the
        // other invariants (extra lifecycle tasks don't affect metadata-compilation existence).
        dir.resolve("gradle.properties")
            .writeText("kmptargets.targets=$selection\nkmptargets.umbrellaTasks=true\n")
        val project = ProjectBuilder.builder().withProjectDir(dir.toFile()).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("com.rsicarelli.kmptargets")
        ext(project).block()
        (project as ProjectInternal).evaluate()
        return project
    }

    private fun ext(project: Project): KmpTargetsExtension =
        project.extensions.getByType(KmpTargetsExtension::class.java)

    private companion object {
        const val COMMON_MAIN_METADATA = "compileCommonMainKotlinMetadata"
    }
}
