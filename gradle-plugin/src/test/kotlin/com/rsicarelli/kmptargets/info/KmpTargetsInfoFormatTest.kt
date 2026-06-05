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

    private fun format(
        selectionIds: List<String> = listOf("jvm"),
        supportedIds: List<String> = listOf("jvm"),
        supportsDeclared: Boolean = true,
        registeredIds: List<String> = listOf("jvm"),
        originLabel: String = "built-in default (all)",
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
        )
}
