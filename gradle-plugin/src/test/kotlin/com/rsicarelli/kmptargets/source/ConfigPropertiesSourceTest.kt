package com.rsicarelli.kmptargets.source

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ConfigPropertiesSourceTest {

    @Test
    fun `given no config file when read then returns null`(@TempDir dir: Path) {
        assertNull(ConfigPropertiesSource(dir.toFile(), ConfigKeys.COMMITTED_FILE).read())
    }

    @Test
    fun `given config file with known keys when read then returns every entry`(@TempDir dir: Path) {
        dir.resolve("kmp-targets.properties")
            .writeText("kmptargets.targets=jvm,iosArm64\nkmptargets.hierarchyTemplate=false\n")
        assertEquals(
            mapOf(
                "kmptargets.targets" to "jvm,iosArm64",
                "kmptargets.hierarchyTemplate" to "false",
            ),
            ConfigPropertiesSource(dir.toFile(), ConfigKeys.COMMITTED_FILE).read(),
        )
    }

    @Test
    fun `given empty config file when read then returns an empty map not null`(@TempDir dir: Path) {
        // File present but empty is not the same as file absent: absent defers the whole layer,
        // present-but-empty just contributes no keys.
        dir.resolve("kmp-targets.properties").writeText("")
        assertEquals(
            emptyMap(),
            ConfigPropertiesSource(dir.toFile(), ConfigKeys.COMMITTED_FILE).read(),
        )
    }

    @Test
    fun `given blank value for a key when read then the entry is present and empty`(
        @TempDir dir: Path
    ) {
        // Preserves the absent-vs-blank contract per key: "" = present but not overriding.
        dir.resolve("kmp-targets.properties").writeText("kmptargets.targets=\n")
        assertEquals(
            mapOf("kmptargets.targets" to ""),
            ConfigPropertiesSource(dir.toFile(), ConfigKeys.COMMITTED_FILE).read(),
        )
    }

    @Test
    fun `given comments and blank lines when read then they are ignored`(@TempDir dir: Path) {
        dir.resolve("kmp-targets.properties")
            .writeText("# team-wide selection\n\nkmptargets.targets=jvm\n")
        assertEquals(
            mapOf("kmptargets.targets" to "jvm"),
            ConfigPropertiesSource(dir.toFile(), ConfigKeys.COMMITTED_FILE).read(),
        )
    }

    @Test
    fun `given leading whitespace around a value when read then it is stripped`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.properties").writeText("kmptargets.targets=  jvm\n")
        assertEquals(
            mapOf("kmptargets.targets" to "jvm"),
            ConfigPropertiesSource(dir.toFile(), ConfigKeys.COMMITTED_FILE).read(),
        )
    }

    @Test
    fun `given the personal file name when read then that file is read instead`(
        @TempDir dir: Path
    ) {
        dir.resolve("kmp-targets.local.properties").writeText("kmptargets.targets=android\n")
        assertEquals(
            mapOf("kmptargets.targets" to "android"),
            ConfigPropertiesSource(dir.toFile(), ConfigKeys.LOCAL_FILE).read(),
        )
        assertNull(ConfigPropertiesSource(dir.toFile(), ConfigKeys.COMMITTED_FILE).read())
    }
}
