package com.rsicarelli.kmptargets.source

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LocalPropertiesSourceTest {

    @Test
    fun `given local properties with the key when read then returns the value`(@TempDir dir: Path) {
        dir.resolve("local.properties").writeText("KMP_TARGETS=android,iosArm64\n")
        val source = LocalPropertiesSource(dir.toFile(), "KMP_TARGETS")
        assertEquals("android,iosArm64", source.read())
    }

    @Test
    fun `given local properties without the key when read then returns null`(@TempDir dir: Path) {
        dir.resolve("local.properties").writeText("OTHER_KEY=value\n")
        val source = LocalPropertiesSource(dir.toFile(), "KMP_TARGETS")
        assertNull(source.read())
    }

    @Test
    fun `given no local properties file when read then returns null`(@TempDir dir: Path) {
        val source = LocalPropertiesSource(dir.toFile(), "KMP_TARGETS")
        assertNull(source.read())
    }

    @Test
    fun `given local properties with leading whitespace around value when read then leading is stripped`(
        @TempDir dir: Path
    ) {
        // java.util.Properties strips leading whitespace from values but keeps trailing
        dir.resolve("local.properties").writeText("KMP_TARGETS=  android\n")
        val source = LocalPropertiesSource(dir.toFile(), "KMP_TARGETS")
        assertEquals("android", source.read())
    }

    @Test
    fun `given local properties with empty value when read then returns empty string`(
        @TempDir dir: Path
    ) {
        dir.resolve("local.properties").writeText("KMP_TARGETS=\n")
        val source = LocalPropertiesSource(dir.toFile(), "KMP_TARGETS")
        assertEquals("", source.read())
    }

    @Test
    fun `given local properties with comments when read then comments are ignored`(
        @TempDir dir: Path
    ) {
        dir.resolve("local.properties")
            .writeText(
                """
                # set KMP_TARGETS to override the default selection
                KMP_TARGETS=appleMobile
                """
                    .trimIndent()
            )
        val source = LocalPropertiesSource(dir.toFile(), "KMP_TARGETS")
        assertEquals("appleMobile", source.read())
    }

    @Test
    fun `given local properties with multiple keys when read then returns the requested one`(
        @TempDir dir: Path
    ) {
        dir.resolve("local.properties")
            .writeText(
                """
                sdk.dir=/Users/foo/Library/Android/sdk
                KMP_TARGETS=android
                org.gradle.jvmargs=-Xmx2g
                """
                    .trimIndent()
            )
        val source = LocalPropertiesSource(dir.toFile(), "KMP_TARGETS")
        assertEquals("android", source.read())
    }

    @Test
    fun `given different property name when read then looks up that name`(@TempDir dir: Path) {
        dir.resolve("local.properties").writeText("MY_CUSTOM_KEY=value\n")
        val source = LocalPropertiesSource(dir.toFile(), "MY_CUSTOM_KEY")
        assertEquals("value", source.read())
    }
}
