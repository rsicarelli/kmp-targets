package com.rsicarelli.kmptargets

import com.rsicarelli.kmptargets.model.KmpTarget
import com.rsicarelli.kmptargets.model.KmpTargetSet
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure decision/message facts of the android-without-AGP advisory (#51): `androidTarget` in the
 * active set can only register when an Android Gradle plugin is applied — KGP's `androidTarget()`
 * is a FATAL diagnostic without one — so the policy flags exactly the active-android-no-AGP
 * combination. No Gradle here; the wiring (skip + warnOrFail + dedup) is proven by
 * [AndroidAgpAdvisoryInProcessTest].
 */
class AndroidAgpAdvisoryTest {

    private val android = KmpTarget.Jvm.Android
    private val jvm = KmpTarget.Jvm.Desktop

    @Test
    fun `given androidTarget active and no android plugin when the policy is computed then it flags`() {
        assertTrue(
            shouldWarnAndroidWithoutAgp(
                active = KmpTargetSet.of(android, jvm),
                androidPluginApplied = false,
            )
        )
    }

    @Test
    fun `given androidTarget active and an android plugin applied when the policy is computed then it does not flag`() {
        assertFalse(
            shouldWarnAndroidWithoutAgp(
                active = KmpTargetSet.of(android, jvm),
                androidPluginApplied = true,
            )
        )
    }

    @Test
    fun `given androidTarget not in the active set and no android plugin when the policy is computed then it does not flag`() {
        assertFalse(
            shouldWarnAndroidWithoutAgp(active = KmpTargetSet.of(jvm), androidPluginApplied = false)
        )
    }

    @Test
    fun `given an empty active set when the policy is computed then it does not flag`() {
        assertFalse(
            shouldWarnAndroidWithoutAgp(active = KmpTargetSet.empty, androidPluginApplied = false)
        )
    }

    @Test
    fun `given a module path when the warning is built then it names the leaf the skip the fix and the recipe`() {
        val message = androidWithoutAgpWarning(":feature")
        assertContains(message, ":feature")
        assertContains(message, "[androidTarget]")
        assertContains(message, "the target was not registered")
        assertContains(message, "com.android.library")
        assertContains(message, "com.android.application")
        assertContains(message, "before supports { }")
        // #76: the message doubles as the discovery vehicle for the selection-gated idiom — it
        // names the gate and links the recipe that covers the exemption and downstream-read traps.
        assertContains(message, "gate it on the selection")
        assertContains(
            message,
            "https://rsicarelli.github.io/kmp-targets/user-guide/recipes/#selection-gated-eager-agp-application",
        )
    }

    @Test
    fun `given the mirrored android plugin id list when compared then it matches KGP's full set`() {
        // Pins the guard against narrowing to library/application only: the guard SUPPRESSES
        // registration, so missing an id KGP accepts (dynamic-feature, test, …) would wrongly
        // skip android on a module where KGP's androidTarget() succeeds.
        assertEquals(
            listOf(
                "com.android.application",
                "com.android.library",
                "com.android.dynamic-feature",
                "com.android.test",
                "com.android.instantapp",
                "com.android.feature",
            ),
            androidPluginIds,
        )
    }
}
