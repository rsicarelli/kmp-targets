package com.rsicarelli.kmptargets.info

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.parser.presetNames
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class KmpTargetsInfoFormatTest {

    @Test
    fun `given a populated report when formatted then all sections appear with their values`() {
        val output =
            format(
                selectionIds = listOf("androidTarget", "iosArm64"),
                supportedIds = listOf("androidTarget", "iosArm64", "jvm"),
                supportsDeclared = true,
                registeredIds = listOf("androidTarget", "iosArm64"),
                originLabel = "command line (-Pkmptargets.targets)",
            )
        assertContains(output, "kmp-targets — :shared-core")
        assertContains(output, "Selection (what to build now)")
        assertContains(output, "source:   command line (-Pkmptargets.targets)")
        assertContains(output, "Supported (what this module can build)")
        assertContains(output, "declared: yes")
        assertContains(output, "androidTarget, iosArm64, jvm")
        assertContains(output, "Registered (selection ∩ supported)")
        assertContains(output, "Vocabulary")
    }

    @Test
    fun `given supports never declared when formatted then the output states the module registers nothing`() {
        val output =
            format(
                selectionIds = listOf("jvm"),
                supportedIds = emptyList(),
                supportsDeclared = false,
                registeredIds = emptyList(),
            )
        assertContains(
            output,
            "declared: no — this module never called supports { }; it registers no targets",
        )
        assertContains(output, "(none — no supported targets declared)")
    }

    @Test
    fun `given disjoint selection and supported when formatted then the empty intersection is explicit`() {
        val output =
            format(
                selectionIds = listOf("wasmJs"),
                supportedIds = listOf("jvm"),
                supportsDeclared = true,
                registeredIds = emptyList(),
            )
        assertContains(
            output,
            "(none — the selection matches nothing this module supports; nothing registers)",
        )
    }

    @Test
    fun `given a selection narrowed to nothing when formatted then the empty selection is explicit`() {
        val output =
            format(
                selectionIds = emptyList(),
                supportedIds = listOf("jvm"),
                supportsDeclared = true,
                registeredIds = emptyList(),
            )
        assertContains(output, "targets:  (none — the selection narrows to nothing)")
        // The registered section reuses the selection explanation, not the disjoint one.
        assertFalse("matches nothing this module supports" in output, output)
    }

    @Test
    fun `given the vocabulary when formatted then every preset name and leaf id is present`() {
        val output = format()
        presetNames.forEach { preset ->
            assertContains(output, preset, false, "missing preset '$preset' in:\n$output")
        }
        KmpTarget.all.forEach { leaf ->
            assertContains(output, leaf.id, false, "missing leaf '${leaf.id}' in:\n$output")
        }
    }

    @Test
    fun `given the report when formatted then it never references Project state beyond the passed path`() {
        // Guard for the config-cache contract: the formatter is a pure function of primitives, so
        // its output for identical inputs is identical.
        assertTrue(format() == format())
    }

    @Test
    fun `given registered leaves the host cannot compile when formatted then each is marked with the host name`() {
        val output =
            format(
                registeredIds = listOf("iosArm64", "jvm", "linuxX64"),
                hostImpossibleIds = listOf("iosArm64"),
                hostLabel = "LINUX_X64",
            )
        assertContains(output, "iosArm64 (not compilable on this host: LINUX_X64)")
        assertFalse("jvm (not compilable" in output, output)
        assertFalse("linuxX64 (not compilable" in output, output)
    }

    @Test
    fun `given all registered leaves compilable when formatted then no host annotation appears`() {
        val output =
            format(
                registeredIds = listOf("iosArm64", "jvm"),
                hostImpossibleIds = emptyList(),
                hostLabel = "MACOS_ARM64",
            )
        assertFalse("not compilable" in output, output)
    }

    @Test
    fun `given a renamed jvm when formatted then the registered line annotates the custom name`() {
        // The rename (issue #49) must be visible where confusion starts: the report explains why
        // the build has `compileKotlinDesktop` tasks but no `jvm`-named target.
        val output = format(registeredIds = listOf("iosArm64", "jvm"), jvmRegisteredAs = "desktop")
        assertContains(output, "jvm (registered as: desktop)")
        assertFalse("iosArm64 (registered as" in output, output)
    }

    @Test
    fun `given a renamed jvm that is also host-impossible when formatted then both markers compose`() {
        val output =
            format(
                registeredIds = listOf("jvm"),
                hostImpossibleIds = listOf("jvm"),
                hostLabel = "LINUX_X64",
                jvmRegisteredAs = "desktop",
            )
        assertContains(
            output,
            "jvm (registered as: desktop) (not compilable on this host: LINUX_X64)",
        )
    }

    @Test
    fun `given no rename when formatted then no registered-as annotation appears`() {
        val output = format(registeredIds = listOf("iosArm64", "jvm"))
        assertFalse("registered as" in output, output)
    }

    @Test
    fun `given androidTarget registered but skipped for want of an android plugin when formatted then it is marked skipped`() {
        val output =
            format(registeredIds = listOf("androidTarget", "jvm"), androidWithoutAgp = true)
        assertContains(output, "androidTarget (skipped: no Android plugin applied)")
        assertFalse("jvm (skipped" in output, output)
    }

    @Test
    fun `given an android plugin applied when formatted then no skipped annotation appears`() {
        val output =
            format(registeredIds = listOf("androidTarget", "jvm"), androidWithoutAgp = false)
        assertFalse("skipped" in output, output)
    }

    @Test
    fun `given an inert module when formatted then the registered section carries the inert line`() {
        // The advisory's consequence rendered where debugging starts (#71): the registered section
        // explains that the metadata compilation is doomed and names the build-logic gate.
        val output =
            format(
                selectionIds = listOf("wasmJs"),
                supportedIds = listOf("jvm"),
                registeredIds = emptyList(),
                inertModule = true,
            )
        assertContains(
            output,
            "inert:    zero targets registered — commonMain metadata compilation will fail; " +
                "gate on registered().isEmpty()",
        )
    }

    @Test
    fun `given a non-inert module when formatted then no inert line appears`() {
        val output = format(registeredIds = listOf("jvm"))
        assertFalse("inert:" in output, output)
    }

    @Test
    fun `given an android-skip inert module when formatted then the skip marker and the inert line compose`() {
        // The #51 skip can leave the module with zero actual registrations while the abstract
        // `selection ∩ supported` line still lists androidTarget — both renderings must coexist.
        val output =
            format(
                selectionIds = listOf("androidTarget"),
                supportedIds = listOf("androidTarget"),
                registeredIds = listOf("androidTarget"),
                androidWithoutAgp = true,
                inertModule = true,
            )
        assertContains(output, "androidTarget (skipped: no Android plugin applied)")
        assertContains(output, "inert:")
    }

    @Test
    fun `given a jvm-less module when formatted then the registered section carries the jvm-less line`() {
        // The advisory's consequence rendered where debugging starts (#72): the registered section
        // explains why the klibs build but the metadata compilation fails, and names the scoped
        // build-logic gate.
        val output =
            format(
                selectionIds = listOf("iosArm64"),
                supportedIds = listOf("iosArm64", "jvm"),
                registeredIds = listOf("iosArm64"),
                nativeOnlyMetadata = true,
            )
        assertContains(
            output,
            "jvm-less: no JVM-family target registered — *KotlinMetadata* compilations reject " +
                "@JvmInline etc. while the klibs compile; gate on registered(jvmFamily).isEmpty()",
        )
    }

    @Test
    fun `given a module with a registered jvm-family leaf when formatted then no jvm-less line appears`() {
        val output = format(registeredIds = listOf("jvm"))
        assertFalse("jvm-less:" in output, output)
    }

    @Test
    fun `given the vocabulary when formatted then exactly the deprecated leaves carry the marker`() {
        val output = format(deprecatedIds = listOf("macosX64", "tvosX64", "watchosX64"))
        assertContains(output, "macosX64 (deprecated)")
        assertContains(output, "tvosX64 (deprecated)")
        assertContains(output, "watchosX64 (deprecated)")
        // iosX64 is tier-3 but NOT deprecated — it must stay unmarked.
        assertFalse("iosX64 (deprecated)" in output, output)
    }

    private fun format(
        selectionIds: List<String> = listOf("jvm"),
        supportedIds: List<String> = listOf("jvm"),
        supportsDeclared: Boolean = true,
        registeredIds: List<String> = listOf("jvm"),
        originLabel: String = "built-in default (all)",
        hostImpossibleIds: List<String> = emptyList(),
        hostLabel: String = "",
        deprecatedIds: List<String> = emptyList(),
        jvmRegisteredAs: String? = null,
        androidWithoutAgp: Boolean = false,
        inertModule: Boolean = false,
        nativeOnlyMetadata: Boolean = false,
    ): String =
        formatKmpTargetsInfo(
            projectPath = ":shared-core",
            selectionIds = selectionIds,
            supportedIds = supportedIds,
            supportsDeclared = supportsDeclared,
            registeredIds = registeredIds,
            originLabel = originLabel,
            presetNames = presetNames,
            leafIds = KmpTarget.all.map { it.id }.sorted(),
            hostImpossibleIds = hostImpossibleIds,
            hostLabel = hostLabel,
            deprecatedIds = deprecatedIds,
            jvmRegisteredAs = jvmRegisteredAs,
            androidWithoutAgp = androidWithoutAgp,
            inertModule = inertModule,
            nativeOnlyMetadata = nativeOnlyMetadata,
        )
}
