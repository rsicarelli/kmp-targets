package com.rsicarelli.kmptargets.doctor

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pure closure join (#80): given this module's needed leaves and the parsed doctor-data of its
 * `project(...)` dependencies, flag the edges where a dependency registers none of a needed leaf.
 * Best-effort and project-edges only — the Gradle layer (lenient ArtifactView) already drops
 * external and plugin-less dependencies, so this logic never sees them (limit #1). The single
 * hardcoded approximation lives here: an android consumer is satisfied by a jvm-only producer
 * (limit #2).
 */
class DoctorClosureTest {

    @Test
    fun `given a dependency missing a needed leaf when gaps are computed then the edge is flagged`() {
        val gaps =
            computeClosureGaps(
                neededLeafIds = listOf("iosArm64", "jvm"),
                dependencies =
                    listOf(DoctorData(path = ":core-jvm", registeredLeafIds = listOf("jvm"))),
            )
        assertEquals(listOf(ClosureGap(":core-jvm", listOf("iosArm64"))), gaps)
    }

    @Test
    fun `given a dependency registering every needed leaf when gaps are computed then there is no gap`() {
        val gaps =
            computeClosureGaps(
                neededLeafIds = listOf("iosArm64", "jvm"),
                dependencies =
                    listOf(
                        DoctorData(
                            ":core",
                            registeredLeafIds = listOf("iosArm64", "jvm", "linuxX64"),
                        )
                    ),
            )
        assertTrue(gaps.isEmpty(), gaps.toString())
    }

    @Test
    fun `given an android consumer and a jvm-only dependency when gaps are computed then the fallback satisfies it`() {
        // The one hardcoded approximation: Gradle's android variant resolves a jvm-only library, so
        // an androidTarget consumer is satisfied by a jvm-only producer.
        val gaps =
            computeClosureGaps(
                neededLeafIds = listOf("androidTarget"),
                dependencies = listOf(DoctorData(":core-jvm", registeredLeafIds = listOf("jvm"))),
            )
        assertTrue(gaps.isEmpty(), gaps.toString())
    }

    @Test
    fun `given a jvm consumer and an android-only dependency when gaps are computed then there is no reverse fallback`() {
        val gaps =
            computeClosureGaps(
                neededLeafIds = listOf("jvm"),
                dependencies =
                    listOf(DoctorData(":ui-android", registeredLeafIds = listOf("androidTarget"))),
            )
        assertEquals(listOf(ClosureGap(":ui-android", listOf("jvm"))), gaps)
    }

    @Test
    fun `given no dependencies when gaps are computed then the result is empty`() {
        assertTrue(computeClosureGaps(listOf("jvm"), emptyList()).isEmpty())
    }

    @Test
    fun `given doctor data when rendered then parsed then it round-trips`() {
        val data =
            DoctorData(
                path = ":feature-mobile",
                registeredLeafIds = listOf("androidTarget", "iosArm64"),
            )
        assertEquals(data, parseDoctorData(renderDoctorData(data)))
    }

    @Test
    fun `given an inert dependency rendering no targets when parsed then its registered set is empty`() {
        val parsed = parseDoctorData(renderDoctorData(DoctorData(":inert", emptyList())))
        assertEquals(emptyList(), parsed.registeredLeafIds)
    }
}
