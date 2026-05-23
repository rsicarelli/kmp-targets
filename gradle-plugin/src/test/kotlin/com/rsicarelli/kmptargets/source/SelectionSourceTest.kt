package com.rsicarelli.kmptargets.source

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class SelectionSourceTest {

    @Test
    fun `given a source returning a value when read then returns that value`() {
        val source = SelectionSource { "android,iosArm64" }
        assertEquals("android,iosArm64", source.read())
    }

    @Test
    fun `given a source returning null when read then returns null`() {
        val source = SelectionSource { null }
        assertNull(source.read())
    }

    @Test
    fun `given a source returning empty string when read then returns empty string`() {
        // Empty is "present but blank" — parser, not source, decides what that means
        val source = SelectionSource { "" }
        assertEquals("", source.read())
    }

    @Test
    fun `given composed sources when first is non-null then first value wins`() {
        val composed =
            composeSelectionSources(
                SelectionSource { "from-first" },
                SelectionSource { "from-second" },
            )
        assertEquals("from-first", composed.read())
    }

    @Test
    fun `given composed sources when first is null then falls back to second`() {
        val composed =
            composeSelectionSources(SelectionSource { null }, SelectionSource { "from-second" })
        assertEquals("from-second", composed.read())
    }

    @Test
    fun `given composed sources when first is empty then empty wins as present-but-blank`() {
        // An explicitly-set blank value is meaningful (turns off the plugin's preference);
        // it must not be skipped in favour of a later non-empty source.
        val composed =
            composeSelectionSources(SelectionSource { "" }, SelectionSource { "from-second" })
        assertEquals("", composed.read())
    }

    @Test
    fun `given composed sources when all return null then result is null`() {
        val composed = composeSelectionSources(SelectionSource { null }, SelectionSource { null })
        assertNull(composed.read())
    }

    @Test
    fun `given empty composition when read then result is null`() {
        val composed = composeSelectionSources()
        assertNull(composed.read())
    }

    @Test
    fun `given many sources when composed then priority is left-to-right`() {
        val composed =
            composeSelectionSources(
                SelectionSource { null },
                SelectionSource { null },
                SelectionSource { "third" },
                SelectionSource { "fourth" },
            )
        assertEquals("third", composed.read())
    }
}
