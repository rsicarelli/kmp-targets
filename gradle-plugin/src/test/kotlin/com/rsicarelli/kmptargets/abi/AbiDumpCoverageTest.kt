package com.rsicarelli.kmptargets.abi

import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AbiDumpCoverageTest {

    @Test
    fun `given a klib api header listing targets when parsed then it returns those names`() {
        val text =
            """
            // Klib ABI Dump
            // Targets: [iosArm64, js, linuxX64]
            // Rendering settings:
            // - Signature version: 2
            """
                .trimIndent()
        assertEquals(setOf("iosArm64", "js", "linuxX64"), parseKlibApiTargets(text))
    }

    @Test
    fun `given a dump with no targets header when parsed then it returns the empty set`() {
        assertEquals(emptySet(), parseKlibApiTargets("public final class Foo {}\n"))
    }

    @Test
    fun `given covered targets the selection did not register when diffed then it returns the gap sorted`() {
        assertEquals(
            listOf("js", "linuxX64"),
            abiDumpUncovered(
                coveredTargetNames = setOf("jvm", "linuxX64", "js"),
                registeredGradleNames = setOf("jvm"),
            ),
        )
    }

    @Test
    fun `given a full selection covering every dumped target when diffed then there is no gap`() {
        assertEquals(
            emptyList(),
            abiDumpUncovered(
                coveredTargetNames = setOf("jvm", "linuxX64", "js"),
                registeredGradleNames = setOf("jvm", "linuxX64", "js"),
            ),
        )
    }

    @Test
    fun `given a renamed jvm leaf when diffed against a desktop dump dir then it is covered not flagged`() {
        // The dump dir is api/desktop/ because the jvm leaf registered as `desktop` (#49); the diff
        // goes through gradleNames, so it matches and reports no gap.
        assertEquals(
            emptyList(),
            abiDumpUncovered(
                coveredTargetNames = setOf("desktop"),
                registeredGradleNames = setOf("desktop"),
            ),
        )
    }

    @Test
    fun `given a per-target dump directory layout when read then subdir names are covered`(
        @TempDir dir: Path
    ) {
        val api = dir.resolve("api").createDirectory()
        api.resolve("linuxX64").createDirectory()
        api.resolve("js").createDirectory()
        assertEquals(setOf("js", "linuxX64"), readAbiDumpCoveredTargets(api.toFile()))
    }

    @Test
    fun `given a merged klib dump file when read then the header targets are covered`(
        @TempDir dir: Path
    ) {
        val api = dir.resolve("api").createDirectory()
        api.resolve("lib.klib.api").writeText("// Targets: [iosArm64, js, linuxX64]\n")
        // The flat jvm .api file contributes presence only, never a named target (documented
        // limit).
        api.resolve("lib.api").writeText("public final class Foo {}\n")
        assertEquals(setOf("iosArm64", "js", "linuxX64"), readAbiDumpCoveredTargets(api.toFile()))
    }

    @Test
    fun `given no dump directory when read then the covered set is empty`(@TempDir dir: Path) {
        assertEquals(emptySet(), readAbiDumpCoveredTargets(dir.resolve("api").toFile()))
    }
}
